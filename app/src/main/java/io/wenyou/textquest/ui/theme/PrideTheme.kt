package io.wenyou.textquest.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Common flag designs; names are stable preference keys, not content classifications. */
enum class PrideTheme(val label: String, private val accentIndex: Int, vararg stripes: Long) {
    GAY("男同性恋", 0, 0xFF078D70, 0xFF26CEAA, 0xFF98E8C1, 0xFFFFFFFF, 0xFF7BADE2, 0xFF5049CC, 0xFF3D1A78),
    LESBIAN("女同性恋", 0, 0xFFD52D00, 0xFFFF9A56, 0xFFFFFFFF, 0xFFD162A4, 0xFFA30262),
    TRANS("跨性别", 0, 0xFF5BCEFA, 0xFFF5A9B8, 0xFFFFFFFF, 0xFFF5A9B8, 0xFF5BCEFA),
    RAINBOW("彩虹", 0, 0xFFE40303, 0xFFFF8C00, 0xFFFFED00, 0xFF008026, 0xFF24408E, 0xFF732982),
    BI("双性恋", 0, 0xFFD60270, 0xFF9B4F96, 0xFF0038A8),
    PAN("泛性恋", 0, 0xFFFF218C, 0xFFFFD800, 0xFF21B1FF),
    ASEXUAL("无性恋", 3, 0xFF000000, 0xFFA3A3A3, 0xFFFFFFFF, 0xFF800080),
    DEMISEXUAL("半性恋", 1, 0xFFFFFFFF, 0xFF800080, 0xFFA3A3A3),
    GRAYSEXUAL("灰性恋", 0, 0xFF740194, 0xFFAEB0AE, 0xFFFFFFFF, 0xFFAEB0AE, 0xFF740194),
    OMNI("全性恋", 1, 0xFFFF9ACE, 0xFFFF53BF, 0xFF200044, 0xFF6760FE, 0xFF8EA6FF),
    POLY("多性恋", 0, 0xFFF61CB9, 0xFF07D569, 0xFF1C92F6),
    ABRO("流动性取向", 0, 0xFF65C286, 0xFFB4E4CA, 0xFFFFFFFF, 0xFFF4A7B9, 0xFFE66591),
    AROMANTIC("无浪漫倾向", 0, 0xFF3DA542, 0xFFA7D379, 0xFFFFFFFF, 0xFFA9A9A9, 0xFF000000),
    NONBINARY("非二元", 2, 0xFFFFF430, 0xFFFFFFFF, 0xFF9C59D1, 0xFF000000),
    GENDERFLUID("性别流动", 0, 0xFFFF75A2, 0xFFFFFFFF, 0xFFBE18D6, 0xFF000000, 0xFF333EBD),
    GENDERQUEER("性别酷儿", 0, 0xFFB57EDC, 0xFFFFFFFF, 0xFF4A8123),
    AGENDER("无性别", 3, 0xFF000000, 0xFFB9B9B9, 0xFFFFFFFF, 0xFFB8F483, 0xFFFFFFFF, 0xFFB9B9B9, 0xFF000000),
    INTERSEX("间性", 1, 0xFFFFD800, 0xFF7902AA),
    UNLABELLED("不贴标签", 0, 0xFFB7D7A3, 0xFFF9F5E9, 0xFFB9D6F0, 0xFFF2E1A0),
    STRAIGHT("异性恋（黑白旗）", 0, 0xFF000000, 0xFFFFFFFF, 0xFF000000, 0xFFFFFFFF, 0xFF000000, 0xFFFFFFFF);

    val colors = stripes.map { Color(it) }
    val accent get() = colors[accentIndex]
    val stripeWeights get() = if (this == BI || this == DEMISEXUAL) listOf(2f, 1f, 2f) else colors.map { 1f }

    companion object {
        fun fromStored(raw: String?) = entries.firstOrNull { it.name == raw }
    }
}

private fun readableAccent(seed: Color, dark: Boolean): Color {
    // WCAG 4.5:1 for white text in light mode and black text in dark mode.
    var color = seed
    var step = 0
    while (if (dark) color.luminance() < 0.175f else color.luminance() > 0.1833f) {
        step++
        color = lerp(seed, if (dark) Color.White else Color.Black, (step * 0.05f).coerceAtMost(1f))
    }
    return color
}

internal fun prideColors(base: ColorScheme, theme: PrideTheme, dark: Boolean): ColorScheme {
    val accents = theme.colors.filter { it != Color.White && it != Color.Black }.distinct().ifEmpty { theme.colors }
    val primary = readableAccent(theme.accent, dark)
    val secondary = readableAccent(accents.last(), dark)
    val tertiary = readableAccent(accents[accents.size / 2], dark)
    val text = if (dark) Color.Black else Color.White
    return base.copy(
        primary = primary, onPrimary = text, primaryContainer = lerp(base.surface, primary, 0.14f), onPrimaryContainer = base.onSurface,
        secondary = secondary, onSecondary = text, secondaryContainer = lerp(base.surface, secondary, 0.14f), onSecondaryContainer = base.onSurface,
        tertiary = tertiary, onTertiary = text, tertiaryContainer = lerp(base.surface, tertiary, 0.14f), onTertiaryContainer = base.onSurface,
        surfaceTint = primary, background = lerp(base.background, theme.accent, 0.025f),
        surface = lerp(base.surface, theme.accent, 0.025f),
        surfaceContainerLowest = base.surface,
        surfaceContainerLow = lerp(base.surface, theme.accent, 0.025f),
        surfaceContainer = lerp(base.surface, theme.accent, 0.04f),
        surfaceContainerHigh = lerp(base.surface, theme.accent, 0.06f),
        surfaceContainerHighest = lerp(base.surface, theme.accent, 0.08f)
    )
}
