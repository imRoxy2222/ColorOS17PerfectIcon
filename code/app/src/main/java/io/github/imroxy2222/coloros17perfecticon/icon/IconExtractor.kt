package io.github.imroxy2222.coloros17perfecticon.icon

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max

/**
 * 通过 PackageManager 读取桌面可见应用的图标。
 *
 * 不需要 root / Xposed：
 * - 桌面入口用 ACTION_MAIN + CATEGORY_LAUNCHER 查询；
 * - 是否“已适配”以 ColorOS 主题图标包（uxicons）为准，见 [ThemeIconPack]。
 */
class IconExtractor(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    data class LauncherApp(val pkg: String, val label: String, val iconRes: Int)

    data class Layers(
        val composite: Bitmap?,
        val background: Bitmap?,
        val foreground: Bitmap?,
        val monochrome: Bitmap?,
    )

    fun queryLauncherApps(): List<LauncherApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = pm.queryIntentActivities(intent, 0)
        val map = LinkedHashMap<String, LauncherApp>()
        for (info in infos) {
            val activityInfo = info.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg == appContext.packageName) continue
            if (map.containsKey(pkg)) continue
            val iconRes = activityInfo.icon.takeIf { it != 0 }
                ?: activityInfo.applicationInfo?.icon
                ?: 0
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            map[pkg] = LauncherApp(pkg, label, iconRes)
        }
        return map.values.sortedBy { it.label.lowercase() }
    }

    fun detect(app: LauncherApp, pack: ThemeIconPack): AppItem {
        val dayIcon = loadIcon(app.pkg, app.iconRes, night = false)
        val nightIcon = loadIcon(app.pkg, app.iconRes, night = true)
        val adaptiveIcon = dayIcon as? AdaptiveIconDrawable
        val state = when {
            pack.hasNightIcon(app.pkg) -> AdaptState.MODULE
            // 图标包里有这个应用但夜间前景与日间相同：桌面用的仍是包里的图，APK 自带夜间图不会生效
            pack.hasIcon(app.pkg) -> AdaptState.NONE
            isSelfAdaptive(dayIcon, nightIcon) -> AdaptState.SELF
            else -> AdaptState.NONE
        }
        return AppItem(
            pkg = app.pkg,
            label = app.label,
            iconRes = app.iconRes,
            state = state,
            adaptive = adaptiveIcon != null,
            monochrome = adaptiveIcon?.let { hasMonochrome(it) } ?: false,
            preview = dayIcon?.let { renderPreview(it, PREVIEW_SIZE) }?.asImageBitmap(),
            previewNight = nightIcon?.let { renderPreview(it, PREVIEW_SIZE) }?.asImageBitmap(),
        )
    }

    /** 分别用日间/夜间 Configuration 渲染图标，比较差异判断 APK 自身是否带夜间图标。 */
    private fun isSelfAdaptive(day: Drawable?, night: Drawable?): Boolean {
        if (day == null || night == null) return false
        val dayBitmap = runCatching { renderComposite(day, PREVIEW_SIZE) }.getOrNull() ?: return false
        val nightBitmap = runCatching { renderComposite(night, PREVIEW_SIZE) }.getOrNull() ?: return false
        val ratio = diffRatio(dayBitmap, nightBitmap)
        dayBitmap.recycle()
        nightBitmap.recycle()
        return ratio > SELF_ADAPT_THRESHOLD
    }

    /** 有明显颜色差异的像素占比；只算 RGB，忽略抗锯齿级别的细微差别。 */
    private fun diffRatio(a: Bitmap, b: Bitmap): Float {
        if (a.width != b.width || a.height != b.height) return 1f
        val size = a.width * a.height
        val pixelsA = IntArray(size)
        val pixelsB = IntArray(size)
        a.getPixels(pixelsA, 0, a.width, 0, 0, a.width, a.height)
        b.getPixels(pixelsB, 0, b.width, 0, 0, b.width, b.height)
        var changed = 0
        for (i in 0 until size) {
            val x = pixelsA[i]
            val y = pixelsB[i]
            val dr = kotlin.math.abs((x shr 16 and 0xFF) - (y shr 16 and 0xFF))
            val dg = kotlin.math.abs((x shr 8 and 0xFF) - (y shr 8 and 0xFF))
            val db = kotlin.math.abs((x and 0xFF) - (y and 0xFF))
            val da = kotlin.math.abs((x ushr 24) - (y ushr 24))
            if (dr + dg + db + da > 32) changed++
        }
        return changed.toFloat() / size
    }

    fun loadIcon(pkg: String, iconRes: Int, night: Boolean): Drawable? {
        if (iconRes != 0) {
            runCatching { resourcesFor(pkg, night).getDrawable(iconRes, null) }
                .getOrNull()
                ?.let { return it }
        }
        return runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
    }

    private fun resourcesFor(pkg: String, night: Boolean): Resources {
        val base = pm.getResourcesForApplication(pkg)
        val config = Configuration(base.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        // 让位图资源尽量选择最高密度的那一份
        config.densityDpi = max(config.densityDpi, HIGH_DENSITY)
        val packageContext = appContext.createPackageContext(pkg, 0)
        return packageContext.createConfigurationContext(config).resources
    }

    /**
     * 整图：桌面显示的是自适应图标中央 72/108 的可视区域，
     * 因此按 1.5 倍居中放大绘制，得到与桌面上看到的效果一致的方形整图。
     */
    fun renderComposite(drawable: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            canvas.save()
            canvas.scale(1.5f, 1.5f, size / 2f, size / 2f)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            canvas.restore()
        } else {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        return bitmap
    }

    /** 单独一层（背景/前景/单色），保持原始比例铺满画布。 */
    fun renderLayer(drawable: Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    fun extract(icon: Drawable, size: Int, withLayers: Boolean): Layers {
        val composite = runCatching { renderComposite(icon, size) }.getOrNull()
        if (!withLayers || icon !is AdaptiveIconDrawable) {
            return Layers(composite, null, null, null)
        }
        val background = icon.background?.let { runCatching { renderLayer(it, size) }.getOrNull() }
        val foreground = icon.foreground?.let { runCatching { renderLayer(it, size) }.getOrNull() }
        val monochrome = icon.monochrome?.let { runCatching { renderLayer(it, size) }.getOrNull() }
        return Layers(composite, background, foreground, monochrome)
    }

    fun hasVisiblePixels(bitmap: Bitmap): Boolean {
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (pixel in row) {
                if (pixel ushr 24 != 0) return true
            }
        }
        return false
    }

    private fun hasMonochrome(icon: AdaptiveIconDrawable): Boolean {
        val layer = icon.monochrome ?: return false
        val bitmap = runCatching { renderLayer(layer, 64) }.getOrNull() ?: return false
        return hasVisiblePixels(bitmap)
    }

    /** 列表预览：不放大裁切，完整绘制图标的原始范围。 */
    private fun renderPreview(drawable: Drawable, size: Int): Bitmap? =
        runCatching { renderLayer(drawable, size) }.getOrNull()

    private companion object {
        const val PREVIEW_SIZE = 160
        const val HIGH_DENSITY = 640
        /** 日/夜图标有明显差异的像素占比下限。 */
        const val SELF_ADAPT_THRESHOLD = 0.02f
    }
}

/**
 * ColorOS 主题图标包（uxicons）。
 *
 * 桌面显示的第三方图标大多来自这里，而不是 APK 自身：
 * - 模块素材：`/my_product/media/theme/uxicons/<density>/<包名>/`
 * - 安装后生效：`/data/oplus/uxicons/<包名>/`
 *
 * 一个应用“已适配”的判据是：目录存在，并且含有可用的暗色素材——
 * 与日间前景（recfg.png）不同的 `rec_night.png`，或 `nsd.png`（暗影整图：
 * 近黑底板 + 彩色图形）。若夜间素材只是日间图的副本（很多应用直接复制），
 * 夜间仍显示原图，视为未适配。
 */
class ThemeIconPack private constructor(
    private val dirs: Map<String, List<File>>,
) {
    private val nightCache = HashMap<String, Boolean>()

    /** 图标包里的应用数量，为 0 说明路径不可读。 */
    val size: Int get() = dirs.size

    fun hasNightIcon(pkg: String): Boolean {
        dirs[pkg]?.let { return nightOf(it) }
        // 包名 + Activity 形式的目录，例如 com.xt.retouch.uilauncher.LaunchActivity
        val prefix = "$pkg."
        for ((name, list) in dirs) {
            if (name.startsWith(prefix)) return nightOf(list)
        }
        return false
    }

    /** 图标包里是否有该应用（不区分夜间前景是否与日间相同）。 */
    fun hasIcon(pkg: String): Boolean {
        if (dirs.containsKey(pkg)) return true
        val prefix = "$pkg."
        return dirs.keys.any { it.startsWith(prefix) }
    }

    private fun nightOf(dirsOfPkg: List<File>): Boolean = dirsOfPkg.any { dir ->
        synchronized(nightCache) {
            nightCache.getOrPut(dir.absolutePath) { computeNight(dir) }
        }
    }

    private fun computeNight(dir: File): Boolean {
        val day = File(dir, "recfg.png")
        val night = File(dir, "rec_night.png")
        if (night.isFile) {
            if (!day.isFile) return true
            if (runCatching { !night.readBytes().contentEquals(day.readBytes()) }.getOrDefault(false)) {
                return true
            }
        }
        // rec_night 缺失或是日间图的副本时，再看 nsd.png（近黑底板 + 彩色图形的暗影整图）
        val nsd = File(dir, "nsd.png")
        if (!nsd.isFile) return false
        if (!day.isFile) return true
        return runCatching { !nsd.readBytes().contentEquals(day.readBytes()) }.getOrDefault(false)
    }

    companion object {
        private val ROOTS = listOf(
            "/my_product/media/theme/uxicons",
            "/data/oplus/uxicons",
        )
        private val DENSITY = Regex("(?:l|m|h|xh|xxh|xxxh|no|any)dpi")

        fun load(): ThemeIconPack {
            val map = LinkedHashMap<String, MutableList<File>>()
            for (path in ROOTS) {
                val root = File(path)
                if (!root.isDirectory) continue
                for (dir in packageDirs(root)) {
                    val name = dir.name
                    when {
                        // 主题商店的哈希资源 ID，无法映射到包名
                        name.startsWith("plus.") -> Unit
                        // 一个目录同时适配多个包名
                        name.contains('和') -> name.split('和')
                            .filter { it.isNotBlank() }
                            .forEach { map.getOrPut(it) { mutableListOf() } += dir }
                        else -> map.getOrPut(name) { mutableListOf() } += dir
                    }
                }
            }
            return ThemeIconPack(map)
        }

        /** 展开 density 这一层，并跳过临时/备份目录。 */
        private fun packageDirs(root: File): List<File> {
            val children = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()
            return children.flatMap { child ->
                if (DENSITY.matches(child.name)) {
                    child.listFiles()?.filter { it.isDirectory } ?: emptyList()
                } else {
                    listOf(child)
                }
            }.filter { dir ->
                val name = dir.name
                !name.startsWith("temp") && !name.startsWith(".") && !name.endsWith(".bak")
            }
        }
    }
}
