package io.github.imroxy2222.coloros17perfecticon.icon

import android.content.Context

/**
 * 读取随应用打包的 ColorOS 官方适配包名清单（assets/official_packages.txt）。
 *
 * 清单由 `com.oplus.uxdesign` 的 `icon.db` 导出，应用内直接读取，不需要 root。
 * 每行一个包名，`#` 开头为注释。
 */
class OfficialIconSource(private val context: Context) {

    private val packages: Set<String> by lazy { readPackages() }

    fun loadPackages(): Set<String> = packages

    private fun readPackages(): Set<String> = runCatching {
        context.assets.open(ASSET).bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
        }
    }.getOrDefault(emptySet())

    private companion object {
        const val ASSET = "official_packages.txt"
    }
}
