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
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF76D1C1),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF0D5A4F),
    onPrimaryContainer = Color(0xFFA7F7EA),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF31475C),
    onSecondaryContainer = Color(0xFFE4EDF8),
    tertiary = Color(0xFFE4BCAD),
    onTertiary = Color(0xFF42281E),
    tertiaryContainer = Color(0xFF5B3E33),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = Color(0xFF101923),
    onBackground = Color(0xFFE3EAF2),
    surface = Color(0xFF121C27),
    onSurface = Color(0xFFE3EAF2),
    surfaceDim = Color(0xFF0D151E),
    surfaceBright = Color(0xFF2C3946),
    surfaceContainerLowest = Color(0xFF09111A),
    surfaceContainerLow = Color(0xFF14202B),
    surfaceContainer = Color(0xFF182531),
    surfaceContainerHigh = Color(0xFF1D2C38),
    surfaceContainerHighest = Color(0xFF263644),
    surfaceVariant = Color(0xFF3D4A5B),
    onSurfaceVariant = Color(0xFFC2CCD8),
    outline = Color(0xFF8D98A6),
    outlineVariant = Color(0xFF576372),
    inverseSurface = Color(0xFFDDE6F0),
    inverseOnSurface = Color(0xFF22313E),
    inversePrimary = Color(0xFF006A60),
    surfaceTint = Color(0xFF76D1C1),
    scrim = Color(0xFF000000),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF92EDE0),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF475E78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E4FF),
    onSecondaryContainer = Color(0xFF001D36),
    tertiary = Color(0xFF904D33),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBD0),
    onTertiaryContainer = Color(0xFF3B0900),
    background = Color(0xFFFBFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFBFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val AppFont = FontFamily.SansSerif

private fun expressiveTextStyle(
    fontWeight: FontWeight,
    fontSize: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = AppFont,
    fontWeight = fontWeight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

private object ExpressiveTypographyTokens {
    val displayLarge = expressiveTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 60,
        lineHeight = 64,
        letterSpacing = -1.2,
    )
    val displayMedium = expressiveTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 48,
        lineHeight = 52,
        letterSpacing = -0.6,
    )
    val displaySmall = expressiveTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 38,
        lineHeight = 42,
        letterSpacing = -0.4,
    )
    val headlineLarge = expressiveTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 34,
        lineHeight = 40,
        letterSpacing = -0.2,
    )
    val headlineMedium = expressiveTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30,
        lineHeight = 36,
        letterSpacing = -0.1,
    )
    val headlineSmall = expressiveTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26,
        lineHeight = 32,
    )
    val titleLarge = expressiveTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 23,
        lineHeight = 28,
    )
    val titleMedium = expressiveTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18,
        lineHeight = 24,
    )
    val titleSmall = expressiveTextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15,
        lineHeight = 20,
        letterSpacing = 0.1,
    )
    val bodyLarge = expressiveTextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17,
        lineHeight = 25,
        letterSpacing = 0.2,
    )
    val bodyMedium = expressiveTextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15,
        lineHeight = 22,
        letterSpacing = 0.2,
    )
    val bodySmall = expressiveTextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13,
        lineHeight = 18,
        letterSpacing = 0.25,
    )
    val labelLarge = expressiveTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14,
        lineHeight = 18,
        letterSpacing = 0.35,
    )
    val labelMedium = expressiveTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12,
        lineHeight = 16,
        letterSpacing = 0.45,
    )
    val labelSmall = expressiveTextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11,
        lineHeight = 14,
        letterSpacing = 0.5,
    )

    val displaySmallEmphasized = displaySmall.copy(fontWeight = FontWeight.Bold)
    val headlineMediumEmphasized = headlineMedium.copy(fontWeight = FontWeight.Bold)
    val headlineLargeEmphasized = headlineLarge.copy(fontWeight = FontWeight.Bold)
    val titleLargeEmphasized = titleLarge.copy(fontWeight = FontWeight.SemiBold)
    val titleMediumEmphasized = titleMedium.copy(fontWeight = FontWeight.Bold)
    val titleSmallEmphasized = titleSmall.copy(fontWeight = FontWeight.Bold)
    val bodyLargeEmphasized = bodyLarge.copy(fontWeight = FontWeight.Medium)
    val labelLargeEmphasized = labelLarge.copy(fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ExpressiveTypography = Typography().copy(
    displayLarge = ExpressiveTypographyTokens.displayLarge,
    displayMedium = ExpressiveTypographyTokens.displayMedium,
    displaySmall = ExpressiveTypographyTokens.displaySmall,
    headlineLarge = ExpressiveTypographyTokens.headlineLarge,
    headlineMedium = ExpressiveTypographyTokens.headlineMedium,
    headlineSmall = ExpressiveTypographyTokens.headlineSmall,
    titleLarge = ExpressiveTypographyTokens.titleLarge,
    titleMedium = ExpressiveTypographyTokens.titleMedium,
    titleSmall = ExpressiveTypographyTokens.titleSmall,
    bodyLarge = ExpressiveTypographyTokens.bodyLarge,
    bodyMedium = ExpressiveTypographyTokens.bodyMedium,
    bodySmall = ExpressiveTypographyTokens.bodySmall,
    labelLarge = ExpressiveTypographyTokens.labelLarge,
    labelMedium = ExpressiveTypographyTokens.labelMedium,
    labelSmall = ExpressiveTypographyTokens.labelSmall,
    displaySmallEmphasized = ExpressiveTypographyTokens.displaySmallEmphasized,
    headlineLargeEmphasized = ExpressiveTypographyTokens.headlineLargeEmphasized,
    headlineMediumEmphasized = ExpressiveTypographyTokens.headlineMediumEmphasized,
    titleLargeEmphasized = ExpressiveTypographyTokens.titleLargeEmphasized,
    titleMediumEmphasized = ExpressiveTypographyTokens.titleMediumEmphasized,
    titleSmallEmphasized = ExpressiveTypographyTokens.titleSmallEmphasized,
    bodyLargeEmphasized = ExpressiveTypographyTokens.bodyLargeEmphasized,
    labelLargeEmphasized = ExpressiveTypographyTokens.labelLargeEmphasized,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val reducedMotionState by produceState(initialValue = true) {
        value = withContext(Dispatchers.Default) {
            runCatching { platformPrefersReducedMotion() }.getOrDefault(true)
        }
    }
    val reducedMotion = reducedMotionState
    val motionScheme = if (reducedMotion) MotionScheme.standard() else MotionScheme.expressive()

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        motionScheme = motionScheme,
    ) {
        CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                content()
            }
        }
    }
}
