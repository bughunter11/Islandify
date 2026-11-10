package com.islandify.app.core

// PATCHKIT15B: backup import ke liye pure helpers (JVM unit test me chal sakte hain).

internal const val BACKUP_FORMAT_VERSION = 1
internal const val BACKUP_MAX_BYTES = 2 * 1024 * 1024
internal const val BACKUP_MAX_KEY = 96
internal const val BACKUP_MAX_STRING = 200_000
internal const val BACKUP_MAX_SET = 5_000

/**
 * [existing] = the value the app currently stores under that key (null when there is none yet).
 * [t] = the type letter from the backup file: b / i / l / f / s / ss.
 * A key that already holds another type must not be overwritten (it would crash the loaders).
 */
internal fun backupTypeMatches(existing: Any?, t: String): Boolean = when (existing) {
    null -> true
    is Boolean -> t == "b"
    is Int -> t == "i"
    is Long -> t == "l"
    is Float -> t == "f"
    is String -> t == "s"
    is Set<*> -> t == "ss"
    else -> false
}
