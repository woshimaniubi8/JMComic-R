package com.batsd.openjm.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ========== Color Schemes ==========

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31101D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightSurface,
    onBackground = Color(0xFF1C1B1F),
    surface = LightSurface,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF49454E),
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFE0E0E8),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFC4C0CA),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFCFBCFF)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378A),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCBC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF4A2532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkSurface,
    onBackground = Color(0xFFE6E1E5),
    surface = DarkSurface,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF33333D),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF6750A4)
)

// Pure black dark scheme
private val PureBlackDarkColorScheme = DarkColorScheme.copy(
    background = PureBlackSurface,
    surface = PureBlackSurface,
    surfaceContainerLow = PureBlackSurface,
    surfaceContainer = PureBlackSurfaceHigh,
    surfaceContainerHigh = PureBlackSurfaceHigh,
    surfaceVariant = DarkSurfaceVariant
)

/**
 * OpenJM 主题
 * - 支持 Dynamic Color (Android 12+)
 * - 支持 Pure Black 暗色模式
 * - 统一使用 Material 3 Design Tokens
 */
@Composable
fun OpenJMTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                if (pureBlack) {
                    dynamicDarkColorScheme(context).let { scheme ->
                        scheme.copy(
                            background = PureBlackSurface,
                            surface = PureBlackSurface,
                            surfaceContainerLow = PureBlackSurface,
                            surfaceContainer = PureBlackSurfaceHigh,
                            surfaceContainerHigh = PureBlackSurfaceHigh
                        )
                    }
                } else {
                    dynamicDarkColorScheme(context)
                }
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme && pureBlack -> PureBlackDarkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

/**
 * 获取当前主题下调整后的颜色（用于卡片背景等）
 */
object ThemeColors {
    /** 卡片填充背景 */
    @Composable
    fun cardContainer(): Color {
        val scheme = MaterialTheme.colorScheme
        return scheme.surfaceContainerHigh
    }

    /** 选中卡片背景 */
    @Composable
    fun selectedCardContainer(): Color {
        val scheme = MaterialTheme.colorScheme
        return scheme.secondaryContainer
    }

    /** 输入框背景 */
    @Composable
    fun inputSurface(): Color {
        val scheme = MaterialTheme.colorScheme
        return scheme.surfaceContainerHighest
    }
}