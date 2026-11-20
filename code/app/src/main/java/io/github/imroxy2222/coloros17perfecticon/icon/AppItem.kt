package io.github.imroxy2222.coloros17perfecticon.icon

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 桌面上可见的一个应用。
 *
 * [adapted] 表示 ColorOS 主题图标包（uxicons）里已有该应用的暗色前景，
 * 即桌面上已经是适配过的图标，与 APK 自身是否带夜间资源无关。
 * [adaptive] 表示图标是 AdaptiveIconDrawable，可以拆出背景层/前景层。
 * [monochrome] 表示存在可用的单色（主题）图层。
 */
data class AppItem(
    val pkg: String,
    val label: String,
    val iconRes: Int,
    val adapted: Boolean,
    val adaptive: Boolean,
    val monochrome: Boolean,
    val preview: ImageBitmap?,
)
