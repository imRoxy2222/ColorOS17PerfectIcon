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

    class Result(val uri: Uri, val exported: Int, val skipped: Int)

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
            ?: throw IOException("无法在 Download 目录创建 icon.zip")

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
            } ?: throw IOException("无法写入 icon.zip")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            Result(uri, exported, skipped)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            if (t is CancellationException) throw t
            throw t
        }
    }

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
}
