package com.openyap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB59D),
    onPrimary = Color(0xFF5B1E08),
    primaryContainer = Color(0xFF7A2F13),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFB6D2FF),
    onSecondary = Color(0xFF10304E),
    secondaryContainer = Color(0xFF284765),
    onSecondaryContainer = Color(0xFFD8E8FF),
    tertiary = Color(0xFFC8D88F),
    onTertiary = Color(0xFF243600),
    tertiaryContainer = Color(0xFF3A4E11),
    onTertiaryContainer = Color(0xFFE4F5A8),
    background = Color(0xFF161214),
    onBackground = Color(0xFFF1DFE3),
    surface = Color(0xFF1D181A),
    onSurface = Color(0xFFF1DFE3),
    surfaceVariant = Color(0xFF544247),
    onSurfaceVariant = Color(0xFFD8C2C8),
    outline = Color(0xFFA08C92),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF9A3412),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBD0),
    onPrimaryContainer = Color(0xFF3A0C00),
    secondary = Color(0xFF22577A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8FF),
    onSecondaryContainer = Color(0xFF001D32),
    tertiary = Color(0xFF576500),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE6A3),
    onTertiaryContainer = Color(0xFF181E00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF23191C),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF23191C),
    surfaceVariant = Color(0xFFF5DDE2),
    onSurfaceVariant = Color(0xFF534347),
    outline = Color(0xFF857378),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(26.dp),
    large = RoundedCornerShape(34.dp),
    extraLarge = RoundedCornerShape(42.dp),
)

private val DisplayFamily = FontFamily.Serif
private val BodyFamily = FontFamily.SansSerif
private val LabelFamily = FontFamily.Monospace

private val ExpressiveTypography = Typography().copy(
    displayLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 60.sp,
        lineHeight = 64.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.6).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 23.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = LabelFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.35.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = LabelFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.45.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = LabelFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        motionScheme = MotionScheme.expressive(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            content()
        }
    }
}
