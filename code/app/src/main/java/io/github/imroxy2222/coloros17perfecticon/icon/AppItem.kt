package io.github.imroxy2222.coloros17perfecticon.icon

import androidx.compose.ui.graphics.ImageBitmap

/** 暗色图标的来源。 */
enum class AdaptState {
    /** ColorOS 图标包（uxicons）里已有不同的夜间前景，桌面使用的就是它。 */
    MODULE,

    /** 图标包未覆盖，但 APK 自身在夜间模式下会渲染出不同的图标。 */
    SELF,

    /** 两者都没有，暗色下仍是日间图标。 */
    NONE,
}

/**
 * 桌面上可见的一个应用。
 *
 * [state] 表示暗色图标的来源（见 [AdaptState]）。
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
    /** 夜间 Configuration 下渲染的图标，用于和 [preview] 并排对比。 */
    val previewNight: ImageBitmap?,
)
