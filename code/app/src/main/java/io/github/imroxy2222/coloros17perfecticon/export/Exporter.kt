package io.github.imroxy2222.coloros17perfecticon.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import io.github.imroxy2222.coloros17perfecticon.icon.AppItem
import io.github.imroxy2222.coloros17perfecticon.icon.IconExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把选中的应用图标打包成 Download/icon.zip。
 *
 * 结构：
 * icon.zip
 * ├── index.json
 * ├── 未适配/<包名>/icon.png [bg.png fg.png mono.png] [raw/...]
 * └── 已适配/<包名>/icon.png icon2.png [bg.png fg.png mono.png]
 */
class Exporter(private val context: Context, private val extractor: IconExtractor) {

    data class Options(
        val size: Int = 1024,
        val includeAdapted: Boolean = false,
        val layers: Boolean = true,
        val rootRaw: Boolean = false,
    )

    class Result(val uri: Uri, val exported: Int, val skipped: Int, val name: String)

    suspend fun export(
        apps: List<AppItem>,
        options: Options,
        onProgress: (done: Int, total: Int, label: String) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val targets = if (options.includeAdapted) apps else apps.filter { !it.adapted }
        val skipped = apps.size - targets.size
        val rawAssets = if (options.rootRaw) RawAssets(context) else null

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "icon.zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法在 Download 目录创建导出文件")

        try {
            val index = JSONArray()
            var exported = 0

            resolver.openOutputStream(uri)?.let { output ->
                ZipOutputStream(BufferedOutputStream(output, 1 shl 16)).use { zip ->
                    for ((position, app) in targets.withIndex()) {
                        ensureActive()
                        onProgress(position, targets.size, app.label)
                        if (writeApp(zip, app, options, rawAssets, index)) exported++
                    }
                    onProgress(targets.size, targets.size, "")

                    writeBytes(
                        zip,
                        "README.md",
                        buildReadme(options, exported).toByteArray(Charsets.UTF_8),
                        store = true,
                    )

                    val root = JSONObject().apply {
                        put("generatedAt", System.currentTimeMillis())
                        put("size", options.size)
                        put("layers", options.layers)
                        put("includeAdapted", options.includeAdapted)
                        put("count", index.length())
                        put("apps", index)
                    }
                    writeBytes(zip, "index.json", root.toString(2).toByteArray(Charsets.UTF_8), store = false)
                }
            } ?: throw IOException("无法写入导出文件")

            val name = "ColorOS17完美图标_${stamp()}_app_$exported.zip"
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null,
            )
            Result(uri, exported, skipped, name)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            if (t is CancellationException) throw t
            throw t
        }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())

    private fun writeApp(
        zip: ZipOutputStream,
        app: AppItem,
        options: Options,
        rawAssets: RawAssets?,
        index: JSONArray,
    ): Boolean {
        val dir = (if (app.adapted) "已适配/" else "未适配/") + app.pkg
        val files = mutableListOf<String>()

        val dayIcon = extractor.loadIcon(app.pkg, app.iconRes, night = false)
        if (dayIcon != null) {
            val layers = extractor.extract(dayIcon, options.size, options.layers)
            layers.composite?.let {
                writeBitmap(zip, "$dir/icon.png", it)
                files += "icon.png"
            }
            if (options.layers) {
                layers.background?.let {
                    writeBitmap(zip, "$dir/bg.png", it)
                    files += "bg.png"
                }
                layers.foreground?.let {
                    writeBitmap(zip, "$dir/fg.png", it)
                    files += "fg.png"
                }
                layers.monochrome?.takeIf { extractor.hasVisiblePixels(it) }?.let {
                    writeBitmap(zip, "$dir/mono.png", it)
                    files += "mono.png"
                }
            }
        }

        if (app.adapted) {
            val nightIcon = extractor.loadIcon(app.pkg, app.iconRes, night = true)
            if (nightIcon != null) {
                runCatching { extractor.renderComposite(nightIcon, options.size) }.getOrNull()?.let {
                    writeBitmap(zip, "$dir/icon2.png", it)
                    files += "icon2.png"
                }
            }
        }

        if (rawAssets != null) {
            for ((name, bytes) in rawAssets.copyInto(app.pkg)) {
                writeBytes(zip, "$dir/raw/$name", bytes, store = false)
                files += "raw/$name"
            }
        }

        if (files.isEmpty()) return false

        index.put(JSONObject().apply {
            put("package", app.pkg)
            put("label", app.label)
            put("adapted", app.adapted)
            put("adaptive", app.adaptive)
            put("monochrome", app.monochrome)
            put("dir", dir)
            put("files", JSONArray(files))
        })
        return true
    }

    private fun writeBitmap(zip: ZipOutputStream, name: String, bitmap: Bitmap) {
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
        bitmap.recycle()
        writeBytes(zip, name, buffer.toByteArray(), store = true)
    }

    private fun writeBytes(zip: ZipOutputStream, name: String, bytes: ByteArray, store: Boolean) {
        val entry = ZipEntry(name)
        if (store) {
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = CRC32().apply { update(bytes) }.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun buildReadme(options: Options, count: Int): String = buildString {
        append("# ColorOS 17 完美图标 · 导出说明\n\n")
        append("本压缩包由「完美图标」App 导出，用于制作 / 修补 Magisk 模块 **ColorOS16完美图标**")
        append("（id: `ColorOSart`）里 ColorOS 主题图标（uxicons）的素材。\n\n")
        append("## 本次导出\n\n")
        append("- 导出时间：").append(stamp()).append('\n')
        append("- 图标尺寸：").append(options.size).append(" px\n")
        append("- 包含分层素材：").append(if (options.layers) "是" else "否").append('\n')
        append("- 应用数量：").append(count).append('\n')
        append("- 应用目录：`未适配/<包名>/` 表示当前图标包里没有夜间素材，")
        append("`已适配/<包名>/` 表示已有。\n\n")
        append(README_BODY)
    }

    private companion object {
        val README_BODY = """
            |## 1. 目录结构
            |
            |```
            |icon.zip
            |├── README.md              本说明
            |├── index.json             清单：包名、名称、是否已适配、包含的文件
            |├── 未适配/<包名>/
            |│   ├── icon.png           整图，桌面实际显示效果（参考用）
            |│   ├── bg.png             背景层（底板）
            |│   ├── fg.png             前景层（图形，透明底）
            |│   ├── mono.png           单色层（纯黑图形 + alpha）
            |│   ├── icon2.png          APK 自带夜间图（仅部分应用有）
            |│   └── raw/               APK 内的原始图标资源
            |└── 已适配/<包名>/          同上（仅在勾选“导出已适配应用”时出现）
            |```
            |
            |## 2. 每个 PNG 的用途
            |
            || 文件 | 含义 | 透明底 | 说明 |
            || --- | --- | --- | --- |
            || `icon.png` | 合成后的整图 | 否 | 已按桌面可视区域裁切，用来对照“做好后应该长什么样” |
            || `bg.png` | 背景层（底板） | 否 | 图标底色 / 底板层，通常是纯色或渐变 |
            || `fg.png` | 前景层（图形） | 是 | 图形本身，四角透明，叠加在 `bg.png` 上 |
            || `mono.png` | 单色层 | 是 | 纯黑图形 + alpha，供系统按主题色重新着色 |
            || `icon2.png` | APK 自带夜间图 | — | 只有 APK 自身提供不同夜间图标时才存在，仅供参考，不要直接当 `rec_night.png` |
            || `raw/*` | APK 原始素材 | — | 原始 mipmap / drawable，需要更高精度时可回查 |
            |
            |透明底的 PNG 必须是 ARGB_8888、真正的 PNG（不是改扩展名），图形之外区域 alpha = 0。
            |
            |## 3. 模块里的目标文件
            |
            |模块目录：`my_product/media/theme/uxicons/hdpi/<包名>/`，一应用一目录，目录名严格等于包名。
            |
            || 导出文件 | 模块文件 | 尺寸 | 要求 |
            || --- | --- | --- | --- |
            || `bg.png` | `recbg.png` | 240×240 | 不透明、铺满整张画布；不要自己画圆角，形状由桌面裁切 |
            || `fg.png` | `recfg.png` | 240×240 | 透明底图形，居中，不贴边 |
            || `mono.png` | `monochrome.png` | 240×240 | 图形为纯黑 `#000000`，靠 alpha 表达形状 |
            || 需自己生成 | `rec_night.png` | 240×240 | 暗色前景，透明底，内容必须与 `recfg.png` 不同，做法见第 4 节 |
            |
            |`rec_night.png` 没有对应的导出文件，需要你在 `recfg.png` 基础上调色生成，这是本次工作的重点。
            |`icon.png` / `icon2.png` 只是参考整图，不放进模块。
            |
            |转换方法：把导出的 PNG 直接等比缩放到 240×240（高质量重采样，保留 alpha）即可。
            |`fg.png` 已经按 108dp 画布导出、图形位于中央 72/108 安全区内，缩放时**不要再裁切或放大**，
            |否则 `recfg.png` 会比官方素材大一圈。
            |
            |## 4. 制作黑暗模式图标（重点）
            |
            |桌面的取图顺序是：暗色模式下用 `rec_night.png`，没有就回退到 `recfg.png`。
            |因此“适配了暗色”等价于：目录里存在 `rec_night.png`，并且它和 `recfg.png` 的内容**确实不同**。
            |只是把 `recfg.png` 复制一份改名成 `rec_night.png`（内容字节完全一致）等于没有适配，
            |夜间仍然是白天的图。
            |
            |生成步骤：
            |
            |1. 取 `fg.png`，缩放到 240×240，得到日间前景 `recfg.png`。
            |2. 以 `recfg.png` 为基础调整颜色，得到夜间前景，另存为 `rec_night.png`：
            |   - 深色 / 低亮度的图形改为白色或浅灰（推荐 `#FFFFFF` 或 `#F2F2F2`）；
            |   - 需要保留品牌色的（微信绿、支付宝蓝等）改用同色系的高亮版本，提高亮度与饱和度；
            |   - 不要用纯黑或接近黑色的前景，夜间底板上会看不见。
            |3. `recbg.png` 保持不变（同一个底板，日间夜间共用）。
            |4. 检查 `rec_night.png` 与 `recfg.png` 不是同一份文件：`md5sum` 必须不同。
            |
            |调色建议：
            |
            |- 白色图形最稳，可读性最好，也是官方素材最常见的做法。
            |- 图形外围可以加 1~2px 的半透明描边，避免浅色底板下边缘发糊。
            |- 不要把 `rec_night.png` 做成整块色板，它必须是透明底 + 图形。
            |
            |## 5. 大图标（可选，OS16 桌面大图标）
            |
            |需要支持 1×2 / 2×1 / 2×2 大图标时，额外提供：
            |
            || 文件 | 尺寸 | 说明 |
            || --- | --- | --- |
            || `recbg_1x2.png` | 240×820 | 竖长底板，铺满画布 |
            || `recfg_1x2.png` | 240×820 | 图形保持 1×1 比例，垂直居中 |
            || `rec_night_1x2.png` | 240×820 | 夜间前景，规则同上 |
            || `recbg_2x1.png` | 820×240 | 横长底板 |
            || `recfg_2x1.png` / `rec_night_2x1.png` | 820×240 | 图形水平居中 |
            || `recbg_2x2.png` | 704×704 | 大方底板 |
            || `recfg_2x2.png` / `rec_night_2x2.png` | 704×704 | 图形等比放大居中 |
            |
            |大图标不是把 1×1 拉伸：底板铺满新画布，图形按原比例居中，宁可留白也不要变形。
            |不提供大图标时，桌面会回退到 1×1 的那一套。
            |
            |## 6. 放置与校验
            |
            |1. 在模块里建目录：`my_product/media/theme/uxicons/hdpi/<包名>/`。
            |2. 放入 `recbg.png`、`recfg.png`、`rec_night.png`（有单色需求再加 `monochrome.png`）。
            |3. 文件权限统一 `0777`，文件名全小写，`1x2` 用字母 x。
            |4. 不要放入 `*_temp.png`、`.trashed-*`、`.bak` 目录。
            |5. 重装模块或重启桌面后验证：切到暗色模式，图标前景应有变化。
            |
            |自检清单：
            |
            |- [ ] `recbg.png` 整张画布不透明，没有透明像素。
            |- [ ] `recfg.png` / `rec_night.png` 四角完全透明，图形居中不贴边。
            |- [ ] `rec_night.png` 与 `recfg.png` 的内容不同（例如 `md5sum` 不一致）。
            |- [ ] `monochrome.png` 只有黑色 + alpha，没有彩色像素。
            |- [ ] 目录名与包名完全一致（区分大小写）。
            |
            |## 7. 特殊情况
            |
            |- 没有 `bg.png` / `fg.png`：说明该应用不是自适应图标。可从 `icon.png` 手工拆出底板与图形，
            |  或直接把 `icon.png` 缩放到 240×240 当作 `recbg.png`，再单独做一份夜间前景。
            |- 只有 `rec_night.png`：桌面允许只提供前景层，底板由系统按图标主色补全。
            |- 包名对不上：`index.json` 里的 `package` 是桌面入口的真实包名，活动级图标可用
            |  `<包名>.<Activity>` 作为目录名。
            |
        """.trimMargin()
    }
}
