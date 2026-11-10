package com.islandify.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.islandify.app.R
import com.islandify.app.core.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// UPDATEKIT: in-app update from GitHub Releases.
// Release rules: tag = "v1.4" (or "1.4"), and the APK must be attached to the release as an asset (.apk).
// PATCHKIT15B: optional line "SHA256: <64 hex>" in the release notes -> the download is verified against it.
// The new APK must have a HIGHER versionCode and the SAME signing key as the installed app (see README > Releasing).
//
// UPDATEUI3 adds:
//  - auto-install: the installer opens by itself when the download is done (app in front)
//  - "Skip this version": no more pop-up for that version (manual check still shows it)
//  - resume: a broken / cancelled download continues from where it stopped (HTTP Range)
//  - background: the dialog can be hidden while the download keeps running
//  - "What's new" welcome after the app was updated (shown once per version)
//  - ETag cache: GitHub answers "304 not modified" (does not count against the API limit),
//    and when the limit is hit the last known release is used instead of an error

object UpdateConfig {
    const val OWNER = "bughunter11"
    const val REPO = "Islandify"

    /** false = dialog has a "Later" button, but it comes back every time the app is opened.
     *  true  = no "Later" button, the user must update to get rid of the dialog. */
    const val FORCE_UPDATE = false

    /** Open the installer by itself when the download is finished (only while the app is in front). */
    const val AUTO_INSTALL = true

    /** Do not ask GitHub again within this time when we already know we are up to date. */
    const val RECHECK_MS = 15 * 60 * 1000L

    /** A half-downloaded file older than this is thrown away instead of resumed. */
    const val PART_MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000
}

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String? = null,
)

/** Shown once after the app was updated. */
data class WhatsNew(val version: String, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    /** [resumedFrom] > 0 = this download continued an earlier one from that many bytes. */
    data class Downloading(val info: UpdateInfo, val done: Long, val total: Long, val resumedFrom: Long = 0L) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    /** [info] is null when only the version check failed (no dialog), set when a download failed. */
    data class Failed(val info: UpdateInfo?, @StringRes val reason: Int) : UpdateState
}

private class InvalidApk(@StringRes val reason: Int) : Exception()

private class RateLimited : Exception()

object UpdateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** True after the user taps "Later" / "Hide" / "Skip". Reset every time the app is opened. */
    private val _hidden = MutableStateFlow(false)
    val hidden: StateFlow<Boolean> = _hidden

    /** Set once after an update: what changed in the version the user is now running. */
    private val _welcome = MutableStateFlow<WhatsNew?>(null)
    val welcome: StateFlow<WhatsNew?> = _welcome

    private var job: Job? = null
    private var lastCheck = 0L
    private var waitingForInstallPermission = false
    private var welcomeFetch = false

    /** Set by UpdateHost (app screen visible or not). Android blocks opening the installer from the background. */
    @Volatile
    private var foreground = false

    private const val PREFS = "islandify_update"
    private const val K_SKIP = "skip_version"
    private const val K_SEEN = "seen_version"
    private const val K_PENDING_VER = "pending_version"
    private const val K_PENDING_NOTES = "pending_notes"
    private const val K_ETAG = "etag"
    private const val K_BODY = "etag_body"

    /** A line like "SHA256: 3f2a..." (64 hex) in the release notes. The line is hidden from the dialog. */
    private val SHA_LINE = Regex("""(?im)^[^\n]*sha-?256[^\n]*?([0-9a-fA-F]{64})[^\n]*$""")

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun installedVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    private fun sameVersion(a: String, b: String) = !isNewerVersion(a, b) && !isNewerVersion(b, a)

    fun setForeground(value: Boolean) { foreground = value }

    /** Call when the app is opened. Checks GitHub and brings the dialog back if the user had hidden it. */
    fun onAppOpened(ctx: Context) {
        val app = ctx.applicationContext
        _hidden.value = false
        val s = _state.value
        val busy = s is UpdateState.Checking || s is UpdateState.Downloading
        scope.launch(Dispatchers.IO) { cleanOldFiles(app, keepPart = busy) }

        prepareWelcome(app)

        // The user chose "Skip this version": keep the pop-up away (About tab still shows it)
        if (s is UpdateState.Available && prefs(app).getString(K_SKIP, null) == s.info.version) _hidden.value = true

        val have = s is UpdateState.Available || s is UpdateState.Ready
        val fresh = s is UpdateState.UpToDate && System.currentTimeMillis() - lastCheck < UpdateConfig.RECHECK_MS
        if (!busy && !have && !fresh) checkNow(app, manual = false)
    }

    /** Call when the app comes back to the front (for example from the "install unknown apps" screen). */
    fun onResume(ctx: Context) {
        if (waitingForInstallPermission && _state.value is UpdateState.Ready &&
            ctx.packageManager.canRequestPackageInstalls()
        ) install(ctx)
    }

    /** "Later" or "Hide": only closes the dialog. A running download keeps going. */
    fun later() { _hidden.value = true }

    fun showDialog() { _hidden.value = false }

    /** "Skip this version": no pop-up for this version any more. A newer version asks again. */
    fun skipVersion(ctx: Context) {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info
            else -> null
        } ?: return
        prefs(ctx).edit().putString(K_SKIP, info.version).apply()
        _hidden.value = true
    }

    /** [manual] = the user tapped "Check for updates" (then the dialog is shown even if it was hidden or skipped). */
    fun checkNow(ctx: Context, manual: Boolean = true) {
        val app = ctx.applicationContext
        val s = _state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            val current = installedVersion(app)
            val result = withContext(Dispatchers.IO) { runCatching { fetchLatest(app) } }
            lastCheck = System.currentTimeMillis()
            val info = result.getOrNull()
            val failure = result.exceptionOrNull()

            if (welcomeFetch && info != null) {
                welcomeFetch = false
                if (sameVersion(info.version, current) && info.notes.isNotBlank()) {
                    _welcome.value = WhatsNew(current, info.notes)
                } else {
                    markSeen(app)
                }
            }

            when {
                failure is RateLimited -> _state.value = UpdateState.Failed(null, R.string.update_err_rate_limit)
                info == null -> {
                    AppLog.e("update", "version check failed", failure)
                    _state.value = UpdateState.Failed(null, R.string.update_err_network)
                }
                !isNewerVersion(info.version, current) -> _state.value = UpdateState.UpToDate(current)
                info.apkUrl.isEmpty() -> _state.value = UpdateState.Failed(null, R.string.update_err_no_apk)
                else -> {
                    val skipped = prefs(app).getString(K_SKIP, null) == info.version
                    _hidden.value = skipped && !manual
                    if (manual) _hidden.value = false
                    _state.value = UpdateState.Available(info)
                }
            }
        }
    }

    fun download(ctx: Context) {
        val app = ctx.applicationContext
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> s.info
            else -> null
        } ?: return
        val target = apkFile(app, info.version)
        val part = File(target.parentFile, target.name + ".part")
        val resumeFrom = if (part.exists()) part.length() else 0L

        // Remember the notes so the "What's new" welcome works even when offline after the update
        prefs(app).edit().putString(K_PENDING_VER, info.version).putString(K_PENDING_NOTES, info.notes).apply()

        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Downloading(info, resumeFrom, info.sizeBytes, resumeFrom)
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    if (apkProblem(app, target, info.sha256) != null) {
                        downloadTo(info, target)
                        val problem = apkProblem(app, target, info.sha256)
                        if (problem != null) {
                            target.delete()
                            throw InvalidApk(problem)
                        }
                    }
                }
            }
            val err = outcome.exceptionOrNull()
            if (err != null && err !is InvalidApk) AppLog.e("update", "download failed", err)
            val result: UpdateState = when {
                err == null -> UpdateState.Ready(info, target)
                err is InvalidApk -> UpdateState.Failed(info, err.reason)
                else -> UpdateState.Failed(info, R.string.update_err_download)
            }
            _state.value = result
            // The user hid the dialog while it was downloading: bring it back with the result
            if (_hidden.value) _hidden.value = false

            if (result is UpdateState.Ready && UpdateConfig.AUTO_INSTALL) {
                delay(700)   // let the "Ready" moment be seen, then open the installer
                if (_state.value is UpdateState.Ready && foreground) install(app)
            }
        }
    }

    /** Stops the download. The half file stays on disk, so the next "Update now" resumes from there. */
    fun cancel() {
        val s = _state.value as? UpdateState.Downloading ?: return
        job?.cancel()
        _state.value = UpdateState.Available(s.info)
    }

    fun install(ctx: Context) {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            // Android asks the user once: "Allow from this source". We continue in onResume().
            waitingForInstallPermission = true
            val opened = runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { AppLog.e("update", "unknown-sources screen failed", it) }.isSuccess
            if (!opened) {
                waitingForInstallPermission = false
                _state.value = UpdateState.Failed(ready.info, R.string.update_err_install)
            }
            return
        }
        waitingForInstallPermission = false
        val started = runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", ready.file)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { AppLog.e("update", "installer intent failed", it) }.isSuccess
        // Retry = download() sees the valid APK on disk and goes straight back to Ready.
        if (!started) _state.value = UpdateState.Failed(ready.info, R.string.update_err_install)
    }

    // ---------------------------------------------------------------- "what's new" after an update

    /** Decides if the welcome sheet is due: the installed version differs from the one we saw last time. */
    private fun prepareWelcome(app: Context) {
        val p = prefs(app)
        val current = installedVersion(app)
        val seen = p.getString(K_SEEN, null)
        if (seen == null) {
            p.edit().putString(K_SEEN, current).apply()   // first run: nothing to show
        } else if (!sameVersion(seen, current) && _welcome.value == null) {
            val pendingVer = p.getString(K_PENDING_VER, null)
            val pendingNotes = p.getString(K_PENDING_NOTES, null).orEmpty()
            if (pendingVer != null && sameVersion(pendingVer, current) && pendingNotes.isNotBlank()) {
                _welcome.value = WhatsNew(current, pendingNotes)
            } else {
                welcomeFetch = true   // updated outside the app: read the notes from GitHub on the next check
            }
        }
    }

    private fun markSeen(app: Context) {
        prefs(app).edit()
            .putString(K_SEEN, installedVersion(app))
            .remove(K_PENDING_VER).remove(K_PENDING_NOTES)
            .apply()
    }

    fun dismissWelcome(ctx: Context) {
        markSeen(ctx.applicationContext)
        _welcome.value = null
    }

    // ---------------------------------------------------------------- network

    private fun parseRelease(text: String): UpdateInfo {
        val json = JSONObject(text)
        var apkUrl = ""
        var size = 0L
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
        }
        val rawNotes = json.optString("body")
        val sha = SHA_LINE.find(rawNotes)?.groupValues?.get(1)?.lowercase()
        return UpdateInfo(
            version = json.optString("tag_name").trimStart('v', 'V'),
            notes = rawNotes.replace(SHA_LINE, "").trim(),
            apkUrl = apkUrl,
            sizeBytes = size,
            sha256 = sha,
        )
    }

    private fun fetchLatest(app: Context): UpdateInfo {
        val p = prefs(app)
        val cachedEtag = p.getString(K_ETAG, null)
        val cachedBody = p.getString(K_BODY, null)
        val url = URL("https://api.github.com/repos/${UpdateConfig.OWNER}/${UpdateConfig.REPO}/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Islandify-Updater")
            // A 304 answer is free: it does not count against the 60 requests / hour limit
            if (cachedEtag != null && cachedBody != null) conn.setRequestProperty("If-None-Match", cachedEtag)
            val code = conn.responseCode
            if (code == 404) return UpdateInfo("0", "", "", 0L)   // no release published yet
            if (code == 304 && cachedBody != null) return parseRelease(cachedBody)
            if (code == 403 || code == 429) {   // GitHub API rate limit (60 / hour / IP)
                if (cachedBody != null) return parseRelease(cachedBody)   // use the last known release instead of an error
                throw RateLimited()
            }
            if (code != 200) error("HTTP $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val info = parseRelease(text)   // parse first: never cache something we cannot read
            val etag = conn.getHeaderField("ETag")
            if (etag != null) p.edit().putString(K_ETAG, etag).putString(K_BODY, text).apply()
            return info
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Downloads to "<apk>.part" and renames when complete. If a .part file is already there, only the
     * missing bytes are requested (HTTP Range). A network failure or cancel keeps the .part file.
     */
    private suspend fun downloadTo(info: UpdateInfo, target: File) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        var have = if (part.exists()) part.length() else 0L
        if (info.sizeBytes > 0 && have > info.sizeBytes) {
            part.delete()
            have = 0L
        }
        if (info.sizeBytes > 0 && have == info.sizeBytes) {   // everything was already downloaded
            target.delete()
            if (!part.renameTo(target)) error("rename failed")
            return
        }
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Islandify-Updater")
            if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
            val code = conn.responseCode
            if (code == 416) {   // the half file does not fit the server file: start again from zero
                part.delete()
                return downloadTo(info, target)
            }
            val resumed = code == 206 && have > 0
            if (code != 200 && !resumed) error("HTTP $code")
            if (!resumed) have = 0L   // the server sent the whole file (or this is a fresh start)
            val len = conn.contentLengthLong
            val total = if (len > 0) len + have else info.sizeBytes
            var done = have
            var lastEmit = 0L
            _state.value = UpdateState.Downloading(info, done, total, have)
            conn.inputStream.use { input ->
                FileOutputStream(part, resumed).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 120) {
                            lastEmit = now
                            currentCoroutineContext().ensureActive()
                            _state.value = UpdateState.Downloading(info, done, total, have)
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("size mismatch")   // cut connection: .part is kept
            target.delete()
            if (!part.renameTo(target)) error("rename failed")
        } catch (t: Throwable) {
            // Network trouble and cancel keep the bytes we already have; anything else starts clean next time
            if (t !is IOException && t !is CancellationException) part.delete()
            throw t
        } finally {
            conn.disconnect()
        }
    }

    // ---------------------------------------------------------------- files

    private fun updatesDir(ctx: Context) = File(ctx.cacheDir, "updates")

    private fun apkFile(ctx: Context, version: String): File {
        val safe = version.replace(Regex("[^0-9A-Za-z._-]"), "_")
        return File(updatesDir(ctx), "Islandify-$safe.apk")
    }

    private fun codeOf(pi: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode
        else @Suppress("DEPRECATION") pi.versionCode.toLong()

    private fun signerSet(pi: PackageInfo): Set<String>? {
        val sigs = if (Build.VERSION.SDK_INT >= 28) pi.signingInfo?.apkContentsSigners
        else @Suppress("DEPRECATION") pi.signatures
        return sigs?.map { it.toCharsString() }?.toSet()?.takeIf { it.isNotEmpty() }
    }

    private fun sha256Of(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * null = the file is a complete Islandify APK that is newer than the installed one, signed with the
     * same key (and matches the SHA-256 from the release notes, when there is one).
     * Otherwise the string resource that explains what is wrong.
     */
    @StringRes
    private fun apkProblem(ctx: Context, f: File, sha256: String?): Int? {
        if (!f.exists() || f.length() == 0L) return R.string.update_err_invalid
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(f.absolutePath, flags) ?: return R.string.update_err_invalid
        if (archive.packageName != ctx.packageName) return R.string.update_err_invalid
        val installed = runCatching { pm.getPackageInfo(ctx.packageName, flags) }.getOrNull()
        if (installed != null) {
            if (codeOf(archive) <= codeOf(installed)) return R.string.update_err_not_newer
            val a = signerSet(archive)
            val b = signerSet(installed)
            if (a != null && b != null && a != b) return R.string.update_err_signature
        }
        if (sha256 != null && !sha256.equals(sha256Of(f), ignoreCase = true)) return R.string.update_err_checksum
        return null
    }

    /**
     * Removes APKs that are not newer than the installed version any more, and half downloads that are
     * stale (old, or for a version that is not newer). A fresh half download is kept so it can resume.
     */
    private fun cleanOldFiles(ctx: Context, keepPart: Boolean) {
        val files = updatesDir(ctx).listFiles() ?: return
        val installed = installedVersion(ctx)
        for (f in files) {
            when {
                f.name.endsWith(".part") -> {
                    if (keepPart) continue
                    val ver = f.name.removePrefix("Islandify-").removeSuffix(".apk.part")
                    val stale = System.currentTimeMillis() - f.lastModified() > UpdateConfig.PART_MAX_AGE_MS
                    if (stale || !isNewerVersion(ver, installed)) f.delete()
                }
                f.name.endsWith(".apk") -> if (apkProblem(ctx, f, null) != null) f.delete()
            }
        }
    }
}
