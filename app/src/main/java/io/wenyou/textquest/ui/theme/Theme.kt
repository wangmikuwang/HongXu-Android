package io.wenyou.textquest.ui.theme

import android.os.Build
import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** 外观三态：跟随系统 / 浅色 / 深色。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ThemeStyle {
    MATERIAL, APPLE;
    companion object {
        fun fromStored(raw: String?): ThemeStyle = entries.firstOrNull { it.name == raw } ?: MATERIAL
    }
}

/** Apple HIG 风格：系统蓝、分组背景；继续使用 Android 原生字体与控件。 */
internal fun appleColors(dark: Boolean) = if (dark) darkColorScheme(
    primary = Color(0xFF80B8FF), onPrimary = Color(0xFF002B55),
    primaryContainer = Color(0xFF12395C), onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFFBFC6D0), secondaryContainer = Color(0xFF30343B), onSecondaryContainer = Color.White,
    tertiaryContainer = Color(0xFF253D35), onTertiaryContainer = Color(0xFFDCF5E8),
    background = Color.Black, onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF1C1C1E), onSurface = Color(0xFFF5F5F7),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF242426), surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFFCACAD0), outline = Color(0xFF96969D)
) else lightColorScheme(
    primary = Color(0xFF0066CC), onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F0FF), onPrimaryContainer = Color(0xFF003366),
    secondary = Color(0xFF526070), secondaryContainer = Color(0xFFE9EDF2), onSecondaryContainer = Color(0xFF252A31),
    tertiaryContainer = Color(0xFFE5F3EB), onTertiaryContainer = Color(0xFF214D38),
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF1C1C1E),
    surface = Color.White, onSurface = Color(0xFF1C1C1E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF8F8FA), surfaceContainerHigh = Color(0xFFECECF1), surfaceContainerHighest = Color(0xFFE4E4E9),
    surfaceVariant = Color(0xFFECECF1), onSurfaceVariant = Color(0xFF56565E), outline = Color(0xFF767680)
)
val LocalThemeStyle = staticCompositionLocalOf { ThemeStyle.MATERIAL }
private val AppleShapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(18.dp))


private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = BrandOnPrimary,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    onSecondary = BrandOnSecondary,
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandOnSecondaryContainer,
    tertiary = BrandTertiary,
    tertiaryContainer = BrandTertiaryContainer,
    background = BrandBackgroundLight,
    surface = BrandSurfaceLight
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    tertiaryContainer = Color(0xFF633B48),
    background = BrandBackgroundDark,
    surface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFF49454F)
)

/** 共享主题入口：风格与明暗模式独立；Material You 支持 Android 12+ 壁纸取色。 */
@Composable
fun WenYouTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    style: ThemeStyle = ThemeStyle.MATERIAL,
    prideTheme: PrideTheme? = null,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    val baseColors = when {
        style == ThemeStyle.APPLE -> appleColors(dark)
        dynamicColor && prideTheme == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val colorScheme = prideTheme?.let { prideColors(baseColors, it, dark) } ?: baseColors
    CompositionLocalProvider(LocalThemeStyle provides style) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (style == ThemeStyle.APPLE) AppleTypography else AppTypography,
            shapes = if (style == ThemeStyle.APPLE) AppleShapes else Shapes(large = RoundedCornerShape(20.dp)),
            content = content
        )
    }
}
