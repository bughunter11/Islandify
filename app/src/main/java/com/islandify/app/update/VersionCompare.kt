package com.islandify.app.update

// PATCHKIT15B: version compare ab alag pure-Kotlin file me hai, taaki JVM unit test se chale
// (UpdateManager object Android / Main dispatcher maangta hai).

internal fun versionParts(v: String): List<Int> =
    v.split('.', '-', '_').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

/** true when [latest] is a higher version than [current]. "1.5" == "1.5.0", "1.10" > "1.9". */
internal fun isNewerVersion(latest: String, current: String): Boolean {
    val a = versionParts(latest)
    val b = versionParts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
