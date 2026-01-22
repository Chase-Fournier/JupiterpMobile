package com.jupiterp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Jupiterp Color System
 * Based on the provided design system with orange accent
 */
object JupiterpColors {
    // Primary brand colors
    val Orange = Color(0xFFF6743C)
    val LightOrange = Color(0xFFE28A64)
    val OrangeContainer = Color(0xFFFFF0EB)
    val OrangeContainerDark = Color(0xFF3D2318)
    
    // Light theme colors
    val BgLight = Color(0xFFFFFFFF)
    val BgSecondaryLight = Color(0xFFF5F5F5)
    val TextLight = Color(0xFF1F2937)
    val TextSecondaryLight = Color(0xFF4B5563)
    val OutlineLight = Color(0xFFD1D5DB)
    val DivBorderLight = Color(0xFFE5E7EB)
    val HoverLight = Color(0xFFF3F4F6)
    val SecCodesLight = Color(0xFF6B7280)
    
    // Dark theme colors
    val BgDark = Color(0xFF151922)
    val BgSecondaryDark = Color(0xFF1F2937)
    val TextDark = Color(0xFFFFFFFF)
    val TextSecondaryDark = Color(0xFF9CA3AF)
    val OutlineDark = Color(0xFF374151)
    val DivBorderDark = Color(0xFF3F475A)
    val HoverDark = Color(0xFF374151)
    val SecCodesDark = Color(0xFF8892A8)
    
    // Status colors
    val SuccessLight = Color(0xFF10B981)
    val SuccessDark = Color(0xFF34D399)
    val ErrorLight = Color(0xFFEF4444)
    val ErrorDark = Color(0xFFF87171)
    val WarningLight = Color(0xFFF59E0B)
    val WarningDark = Color(0xFFFBBF24)
    
    // Schedule block colors (pastel palette for course differentiation)
    val ScheduleColors = listOf(
        Color(0xFFB3C8F2), // Soft blue
        Color(0xFFF2B3B3), // Soft red
        Color(0xFFF2EFB3), // Soft yellow
        Color(0xFFB3F2E6), // Soft teal
        Color(0xFFDEB3F2), // Soft purple
        Color(0xFFB8F2B3), // Soft green
        Color(0xFFF2C996), // Soft orange
        Color(0xFFEDAFD6), // Soft pink
        Color(0xFFE6CDE3), // Soft lavender
        Color(0xFFFEDFCC), // Soft peach
        Color(0xFFADE0F6), // Soft sky
        Color(0xFFFDD4F3), // Soft magenta
        Color(0xFFC4BBF1), // Soft violet
        Color(0xFFD3F4BA), // Soft lime
        Color(0xFFAAEFF4), // Soft cyan
        Color(0xFFFFC6B0)  // Soft coral
    )
    
    // Rating colors
    fun ratingColor(rating: Float): Color = when {
        rating >= 4.5f -> Color(0xFF10B981)
        rating >= 4.0f -> Color(0xFF22C55E)
        rating >= 3.5f -> Color(0xFFF59E0B)
        rating >= 3.0f -> Color(0xFFF97316)
        else -> Color(0xFFEF4444)
    }
    
    // Seat availability colors
    fun seatColor(openSeats: Int, totalSeats: Int): Color {
        val ratio = if (totalSeats > 0) openSeats.toFloat() / totalSeats else 0f
        return when {
            openSeats <= 0 -> Color(0xFFEF4444)
            ratio <= 0.1f -> Color(0xFFF97316)
            ratio <= 0.25f -> Color(0xFFF59E0B)
            else -> Color(0xFF10B981)
        }
    }
}

/**
 * Extended color scheme for app-specific colors
 */
data class JupiterpExtendedColors(
    val orange: Color,
    val lightOrange: Color,
    val orangeContainer: Color,
    val textSecondary: Color,
    val divider: Color,
    val sectionCodes: Color,
    val hover: Color,
    val success: Color,
    val warning: Color,
    val scheduleColors: List<Color>
)

val LocalJupiterpColors = staticCompositionLocalOf {
    JupiterpExtendedColors(
        orange = JupiterpColors.Orange,
        lightOrange = JupiterpColors.LightOrange,
        orangeContainer = JupiterpColors.OrangeContainer,
        textSecondary = JupiterpColors.TextSecondaryLight,
        divider = JupiterpColors.DivBorderLight,
        sectionCodes = JupiterpColors.SecCodesLight,
        hover = JupiterpColors.HoverLight,
        success = JupiterpColors.SuccessLight,
        warning = JupiterpColors.WarningLight,
        scheduleColors = JupiterpColors.ScheduleColors
    )
}

private val LightColorScheme = lightColorScheme(
    primary = JupiterpColors.Orange,
    onPrimary = Color.White,
    primaryContainer = JupiterpColors.OrangeContainer,
    onPrimaryContainer = JupiterpColors.Orange,
    secondary = JupiterpColors.LightOrange,
    onSecondary = Color.White,
    secondaryContainer = JupiterpColors.OrangeContainer,
    onSecondaryContainer = JupiterpColors.Orange,
    tertiary = JupiterpColors.Orange,
    onTertiary = Color.White,
    background = JupiterpColors.BgLight,
    onBackground = JupiterpColors.TextLight,
    surface = JupiterpColors.BgLight,
    onSurface = JupiterpColors.TextLight,
    surfaceVariant = JupiterpColors.BgSecondaryLight,
    onSurfaceVariant = JupiterpColors.TextSecondaryLight,
    outline = JupiterpColors.OutlineLight,
    outlineVariant = JupiterpColors.DivBorderLight,
    error = JupiterpColors.ErrorLight,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = JupiterpColors.Orange,
    onPrimary = Color.White,
    primaryContainer = JupiterpColors.OrangeContainerDark,
    onPrimaryContainer = JupiterpColors.Orange,
    secondary = JupiterpColors.LightOrange,
    onSecondary = Color.White,
    secondaryContainer = JupiterpColors.OrangeContainerDark,
    onSecondaryContainer = JupiterpColors.LightOrange,
    tertiary = JupiterpColors.Orange,
    onTertiary = Color.White,
    background = JupiterpColors.BgDark,
    onBackground = JupiterpColors.TextDark,
    surface = JupiterpColors.BgDark,
    onSurface = JupiterpColors.TextDark,
    surfaceVariant = JupiterpColors.BgSecondaryDark,
    onSurfaceVariant = JupiterpColors.TextSecondaryDark,
    outline = JupiterpColors.OutlineDark,
    outlineVariant = JupiterpColors.DivBorderDark,
    error = JupiterpColors.ErrorDark,
    onError = Color.White
)

private val LightExtendedColors = JupiterpExtendedColors(
    orange = JupiterpColors.Orange,
    lightOrange = JupiterpColors.LightOrange,
    orangeContainer = JupiterpColors.OrangeContainer,
    textSecondary = JupiterpColors.TextSecondaryLight,
    divider = JupiterpColors.DivBorderLight,
    sectionCodes = JupiterpColors.SecCodesLight,
    hover = JupiterpColors.HoverLight,
    success = JupiterpColors.SuccessLight,
    warning = JupiterpColors.WarningLight,
    scheduleColors = JupiterpColors.ScheduleColors
)

private val DarkExtendedColors = JupiterpExtendedColors(
    orange = JupiterpColors.Orange,
    lightOrange = JupiterpColors.LightOrange,
    orangeContainer = JupiterpColors.OrangeContainerDark,
    textSecondary = JupiterpColors.TextSecondaryDark,
    divider = JupiterpColors.DivBorderDark,
    sectionCodes = JupiterpColors.SecCodesDark,
    hover = JupiterpColors.HoverDark,
    success = JupiterpColors.SuccessDark,
    warning = JupiterpColors.WarningDark,
    scheduleColors = JupiterpColors.ScheduleColors
)

/**
 * Custom shapes for Material 3 components
 */
val JupiterpShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Typography scale
 */
val JupiterpTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Normal
    ),
    displaySmall = TextStyle(
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Normal
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
)

/**
 * Main theme composable
 */
@Composable
fun JupiterpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    
    CompositionLocalProvider(
        LocalJupiterpColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JupiterpTypography,
            shapes = JupiterpShapes,
            content = content
        )
    }
}

/**
 * Access extended colors from the current theme
 */
object JupiterpTheme {
    val extendedColors: JupiterpExtendedColors
        @Composable
        get() = LocalJupiterpColors.current
}
