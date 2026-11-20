package io.github.imroxy2222.coloros17perfecticon.icon

import androidx.compose.ui.graphics.ImageBitmap

/** 是否被导入的模块图标包覆盖。 */
enum class AdaptState {
    /** 模块 zip 里有该应用的图标目录，暗色模式下桌面会换成包里的图。 */
    MODULE,

    /** 模块 zip 里没有该应用，暗色下仍是原来的图标。 */
    NONE,
}

/**
 * 桌面上可见的一个应用。
 *
 * [state] 表示是否在导入的模块图标包中（见 [AdaptState]）。
 * [adaptive] 表示图标是 AdaptiveIconDrawable，可以拆出背景层/前景层。
 * [monochrome] 表示存在可用的单色（主题）图层。
 */
data class AppItem(
    val pkg: String,
    val label: String,
    val iconRes: Int,
    val state: AdaptState,
    val adaptive: Boolean,
    val monochrome: Boolean,
    val preview: ImageBitmap?,
)
