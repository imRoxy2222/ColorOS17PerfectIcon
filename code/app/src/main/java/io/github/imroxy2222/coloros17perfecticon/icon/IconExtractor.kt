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
import kotlin.math.max

/**
 * 通过 PackageManager 读取桌面可见应用的图标。
 *
 * 不需要 root / Xposed：
 * - 桌面入口用 ACTION_MAIN + CATEGORY_LAUNCHER 查询；
 * - 图标用资源 ID 加载，同一个 ID 分别在日间/夜间 Configuration 下渲染后比较，
 *   以此判断应用是否提供了 night 图标资源。
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

    fun detect(app: LauncherApp): AppItem {
        val day = loadIcon(app.pkg, app.iconRes, night = false)
        val night = loadIcon(app.pkg, app.iconRes, night = true)
        val dayBitmap = day?.let { renderSafe(it, PREVIEW_SIZE) }
        val nightBitmap = night?.let { renderSafe(it, PREVIEW_SIZE) }
        val hasNight = dayBitmap != null && nightBitmap != null && !dayBitmap.sameAs(nightBitmap)
        val adaptiveIcon = (day as? AdaptiveIconDrawable) ?: (night as? AdaptiveIconDrawable)
        return AppItem(
            pkg = app.pkg,
            label = app.label,
            iconRes = app.iconRes,
            hasNight = hasNight,
            adaptive = adaptiveIcon != null,
            monochrome = adaptiveIcon?.let { hasMonochrome(it) } ?: false,
            preview = (day ?: night)?.let { renderPreview(it, PREVIEW_SIZE) }?.asImageBitmap(),
        )
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

    private fun renderSafe(drawable: Drawable, size: Int): Bitmap? =
        runCatching { renderComposite(drawable, size) }.getOrNull()

    /** 列表预览：不放大裁切，完整绘制图标的原始范围。 */
    private fun renderPreview(drawable: Drawable, size: Int): Bitmap? =
        runCatching { renderLayer(drawable, size) }.getOrNull()

    private companion object {
        const val PREVIEW_SIZE = 160
        const val HIGH_DENSITY = 640
    }
}
