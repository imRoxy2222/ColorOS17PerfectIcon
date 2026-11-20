package io.github.imroxy2222.coloros17perfecticon.icon

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 桌面上可见的一个应用。
 *
 * [hasNight] 表示该应用的图标在 night 资源下与亮色资源渲染结果不同，即“已适配暗色”。
 * [adaptive] 表示图标是 AdaptiveIconDrawable，可以拆出背景层/前景层。
 * [monochrome] 表示存在可用的单色（主题）图层。
 */
data class AppItem(
    val pkg: String,
    val label: String,
    val iconRes: Int,
    val hasNight: Boolean,
    val adaptive: Boolean,
    val monochrome: Boolean,
    val preview: ImageBitmap?,
)
