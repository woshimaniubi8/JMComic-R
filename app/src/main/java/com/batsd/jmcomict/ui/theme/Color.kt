package com.batsd.jmcomict.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ========== Color Extension Utilities (inspired by FlClash) ==========

/** 80% 不透明度 */
val Color.opacity80: Color get() = copy(alpha = 0.8f)
/** 60% 不透明度 */
val Color.opacity60: Color get() = copy(alpha = 0.6f)
/** 50% 不透明度 */
val Color.opacity50: Color get() = copy(alpha = 0.5f)
/** 38% 不透明度 */
val Color.opacity38: Color get() = copy(alpha = 0.38f)
/** 30% 不透明度 */
val Color.opacity30: Color get() = copy(alpha = 0.3f)
/** 12% 不透明度 */
val Color.opacity12: Color get() = copy(alpha = 0.12f)
/** 10% 不透明度 */
val Color.opacity10: Color get() = copy(alpha = 0.10f)

/** 将颜色变亮（HSL 增加亮度） */
fun Color.lighten(amount: Float = 10f): Color {
    if (amount <= 0) return this
    val hsl = toHsl()
    return hsl.copy(lightness = (hsl.lightness + amount / 100f).coerceIn(0f, 1f)).toColor()
}

/** 将颜色变暗（HSL 减少亮度） */
fun Color.darken(amount: Float = 10f): Color {
    if (amount <= 0) return this
    if (amount >= 100) return Color.Black
    val hsl = toHsl()
    return hsl.copy(lightness = (hsl.lightness - amount / 100f).coerceIn(0f, 1f)).toColor()
}

/** 混合暗色：根据当前主题明暗使用不同混合因子 */
fun Color.blendDarken(isDark: Boolean, factor: Float = 0.1f): Color {
    return if (isDark) lighten(factor * 100f) else darken(factor * 100f)
}

/** HSL 颜色数据类 */
private data class HslColor(val hue: Float, val saturation: Float, val lightness: Float, val alpha: Float)

private fun Color.toHsl(): HslColor {
    val r = red
    val g = green
    val b = blue
    val maxVal = max(max(r, g), b)
    val minVal = min(min(r, g), b)
    val l = (maxVal + minVal) / 2f

    if (maxVal == minVal) return HslColor(0f, 0f, l, alpha)

    val d = maxVal - minVal
    val s = if (l > 0.5f) d / (2f - maxVal - minVal) else d / (maxVal + minVal)

    val h = when (maxVal) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    return HslColor(h, s, l, alpha)
}

private fun HslColor.toColor(): Color {
    if (saturation == 0f) return Color(alpha, lightness, lightness, lightness)

    fun hueToRgb(p: Float, q: Float, t: Float): Float {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        return when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
    }

    val q = if (lightness < 0.5f) lightness * (1f + saturation) else lightness + saturation - lightness * saturation
    val p = 2f * lightness - q
    val r = hueToRgb(p, q, hue + 1f / 3f)
    val g = hueToRgb(p, q, hue)
    val b = hueToRgb(p, q, hue - 1f / 3f)
    return Color(alpha, r, g, b)
}

// ========== Base Color Tokens ==========

// 品牌色 - 蓝紫渐变风格 (FlClash-inspired)
val BrandBlue = Color(0xFF4A90D9)
val BrandPurple = Color(0xFF7C5CFC)
val BrandViolet = Color(0xFF9B6BFF)

// Light theme surface tokens
val LightSurfaceLow = Color(0xFFF7F7FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHigh = Color(0xFFF0F0F5)
val LightSurfaceVariant = Color(0xFFE8E8F0)

// Dark theme surface tokens
val DarkSurfaceLow = Color(0xFF0F0F14)
val DarkSurface = Color(0xFF18181F)
val DarkSurfaceHigh = Color(0xFF22222A)
val DarkSurfaceVariant = Color(0xFF2A2A35)

// Pure black dark mode
val PureBlackSurface = Color(0xFF000000)
val PureBlackSurfaceHigh = Color(0xFF0A0A0F)