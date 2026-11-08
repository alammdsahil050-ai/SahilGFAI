package com.sahil.gfai

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/** Plain-text conversation memory, hard-capped at 30 MB. Oldest lines are dropped first. */
object MemoryStore {
    const val MAX_BYTES = 30L * 1024 * 1024
    private fun file(c: Context) = File(c.filesDir, "memory.txt")

    @Synchronized
    fun append(c: Context, role: String, text: String) {
        val t = text.trim().replace('\n', ' ').replace('|', '/')
        if (t.isEmpty()) return
        val f = file(c)
        f.appendText("${System.currentTimeMillis()}|$role|$t\n")
        if (f.length() > MAX_BYTES) trim(f)
    }

    private fun trim(f: File) {
        val target = (MAX_BYTES * 0.9).toLong()
        val drop = f.length() - target
        val tmp = File(f.parentFile, "memory.tmp")
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(drop)
            raf.readLine() // skip partial line
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = raf.read(buf); if (n <= 0) break; out.write(buf, 0, n) }
            }
        }
        f.delete(); tmp.renameTo(f)
    }

    @Synchronized
    fun tail(c: Context, maxBytes: Int): String {
        val f = file(c); if (!f.exists() || f.length() == 0L) return ""
        RandomAccessFile(f, "r").use { raf ->
            val start = maxOf(0L, raf.length() - maxBytes)
            raf.seek(start)
            val b = ByteArray((raf.length() - start).toInt()); raf.readFully(b)
            val s = String(b, Charsets.UTF_8)
            return if (start > 0) s.substringAfter('\n', "") else s
        }
    }

    /** Last N turns as (role, text). */
    fun lastTurns(c: Context, n: Int): List<Pair<String, String>> =
        tail(c, 64 * 1024).lines().filter { it.isNotBlank() }.takeLast(n).mapNotNull {
            val p = it.split("|", limit = 3); if (p.size == 3) p[1] to p[2] else null
        }

    /** Compact text for the system prompt. */
    fun promptContext(c: Context, maxBytes: Int = 6000): String =
        tail(c, maxBytes).lines().filter { it.isNotBlank() }.joinToString("\n") {
            val p = it.split("|", limit = 3); if (p.size == 3) "${p[1]}: ${p[2]}" else ""
        }

    fun sizeBytes(c: Context) = file(c).let { if (it.exists()) it.length() else 0L }
    fun clear(c: Context) { file(c).delete() }
}
