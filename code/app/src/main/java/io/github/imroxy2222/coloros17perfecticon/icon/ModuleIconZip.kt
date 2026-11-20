package io.github.imroxy2222.coloros17perfecticon.icon

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 读取用户导入的模块 zip，得到图标包覆盖的包名集合。
 *
 * 兼容两种打包方式：
 * - 模块 zip 里直接是 `my_product/media/theme/uxicons/hdpi/<包名>/`；
 * - 模块 zip 里再套一层 `icons.zip` / `uxicons.zip`，里面是 `uxicons/<包名>/`。
 */
object ModuleIconZip {

    data class Snapshot(val name: String, val packages: Set<String>)

    fun read(context: Context, uri: Uri): Snapshot {
        val name = displayName(context, uri)
        val packages = HashSet<String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            collectEntries(BufferedInputStream(input), packages)
        } ?: throw IllegalStateException("无法读取所选文件")
        return Snapshot(name, packages)
    }

    /**
     * 流式遍历 zip 条目；遇到内嵌的 `.zip`（icons.zip / uxicons.zip）直接接着往下读，
     * 不整包读进内存，避免大包 OOM。
     */
    private fun collectEntries(input: InputStream, out: MutableSet<String>) {
        val zip = ZipInputStream(input)
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                if (entry.name.endsWith(".zip", ignoreCase = true)) {
                    // 不是合法 zip 的条目直接跳过，不影响其他目录
                    runCatching { collectEntries(zip, out) }
                } else {
                    collect(entry.name, out)
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        // 故意不 close：内嵌 zip 用的还是外层流，close 会连带关掉外层
    }

    /**
     * 取出包名，兼容三种布局：
     * - `.../hdpi/<包名>/xxx.png`
     * - `uxicons/<包名>/xxx.png`
     * - `<包名>/xxx.png`（模块里内嵌的 icons.zip 就是这种）
     */
    private fun collect(path: String, out: MutableSet<String>) {
        val parts = path.split('/')
        if (parts.size >= 2) addPackage(parts[0], out)
        for (index in parts.indices) {
            if (parts[index] != "hdpi" && parts[index] != "uxicons") continue
            var next = index + 1
            if (next < parts.size && parts[next] == "hdpi") next++
            if (next < parts.size) addPackage(parts[next], out)
        }
    }

    private fun addPackage(segment: String, out: MutableSet<String>) {
        // 一个目录里塞了两个包名：com.a和com.b
        for (part in segment.split('和')) addSinglePackage(part, out)
    }

    private fun addSinglePackage(segment: String, out: MutableSet<String>) {
        if (segment.length < 3 || !segment.contains('.')) return
        if (segment.startsWith(".") || segment.startsWith("plus.") || segment.startsWith("temp_")) return
        if (segment.endsWith(".bak")) return
        if (!segment.all { it.isLetterOrDigit() || it == '.' || it == '_' }) return
        out.add(segment)
    }

    private fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "模块.zip"
    }
}

/** 把上次导入的包名集合持久化，下次启动直接复用。 */
class ModuleIconStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("module_icon_zip", Context.MODE_PRIVATE)

    fun load(): ModuleIconZip.Snapshot? {
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val packages = prefs.getStringSet(KEY_PACKAGES, null) ?: return null
        if (packages.isEmpty()) return null
        return ModuleIconZip.Snapshot(name, packages.toHashSet())
    }

    fun save(snapshot: ModuleIconZip.Snapshot) {
        prefs.edit()
            .putString(KEY_NAME, snapshot.name)
            .putStringSet(KEY_PACKAGES, HashSet(snapshot.packages))
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_PACKAGES = "packages"
    }
}
