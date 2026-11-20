package io.github.imroxy2222.coloros17perfecticon.export

import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 可选增强：把 APK 里与启动图标相关的原始素材（png/webp）一并导出。
 *
 * 正常情况下 /data/app 下的 APK 当前应用可以直接读取，不需要 root；
 * 只有读不到时才尝试用 su 复制到 cache。
 */
class RawAssets(private val context: Context) {

    fun isRootAvailable(): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor() == 0 && output.contains("uid=0")
    }.getOrDefault(false)

    fun copyInto(pkg: String): List<Pair<String, ByteArray>> {
        val sourceDir = runCatching {
            context.packageManager.getApplicationInfo(pkg, 0).sourceDir
        }.getOrNull() ?: return emptyList()

        val direct = File(sourceDir)
        if (direct.isFile && direct.canRead()) {
            return readZip(direct)
        }
        val cached = copyWithRoot(sourceDir, pkg) ?: return emptyList()
        return try {
            readZip(cached)
        } finally {
            cached.delete()
        }
    }

    private fun copyWithRoot(sourceDir: String, pkg: String): File? = runCatching {
        val dest = File(context.cacheDir, "apk/$pkg.apk")
        dest.parentFile?.mkdirs()
        val command = "cp \"$sourceDir\" \"${dest.absolutePath}\" && chmod 644 \"${dest.absolutePath}\""
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        process.waitFor()
        dest.takeIf { it.isFile && it.length() > 0 }
    }.getOrNull()

    private fun readZip(apk: File): List<Pair<String, ByteArray>> = runCatching {
        val result = mutableListOf<Pair<String, ByteArray>>()
        var total = 0L
        ZipFile(apk).use { zip ->
            val candidates = mutableListOf<ZipEntry>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name
                if (!name.startsWith("res/")) continue
                if (!name.endsWith(".png", ignoreCase = true) && !name.endsWith(".webp", ignoreCase = true)) continue
                val simple = name.substringAfterLast('/')
                if (!simple.contains("ic_launcher") && !simple.startsWith("icon")) continue
                if (entry.size <= 0L || entry.size > MAX_FILE_BYTES) continue
                candidates += entry
            }
            candidates.sortByDescending { it.size }
            for (entry in candidates) {
                if (total >= MAX_TOTAL_BYTES) break
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                if (bytes.isEmpty()) continue
                total += bytes.size
                result += entry.name to bytes
            }
        }
        result
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_FILE_BYTES = 2L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 12L * 1024 * 1024
    }
}
