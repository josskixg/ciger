package com.ciger.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Minimal root shell. One `su` process per call, script fed on stdin so a whole
 * sysfs dump costs a single privilege escalation instead of one per file.
 */
object RootShell {

    @Volatile
    private var cachedRooted: Boolean? = null

    /** Run [script] as root, merged stdout+stderr. Empty string when su is absent/denied. */
    suspend fun sh(script: String, timeoutSeconds: Long = 15): String = withContext(Dispatchers.IO) {
        val proc = try {
            ProcessBuilder("su", "-c", "sh").redirectErrorStream(true).start()
        } catch (e: Exception) {
            return@withContext ""
        }

        val out = StringBuilder()
        val reader = Thread {
            try {
                proc.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { out.append(it).append('\n') }
                }
            } catch (e: Exception) {
                // process died mid-read; keep whatever we got
            }
        }
        reader.start()

        try {
            proc.outputStream.buffered().use { w ->
                w.write(script.toByteArray())
                w.flush()
            }
        } catch (e: Exception) {
            // su rejected us or died — fall through to the timeout/teardown below
        }

        proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        reader.join(2_000)
        if (proc.isAlive) proc.destroyForcibly()
        out.toString()
    }

    /** True when a root shell actually reports uid 0. Result is cached. */
    suspend fun isRooted(): Boolean {
        cachedRooted?.let { return it }
        val ok = sh("id -u").lineSequence().any { it.trim() == "0" }
        cachedRooted = ok
        return ok
    }

    /** Clear the cache so a later grant/denial is re-detected. */
    fun invalidate() {
        cachedRooted = null
    }
}
