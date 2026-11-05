package io.github.xxtvrxx233.wearcompass.presentation.theme

import android.content.Context
import androidx.compose.material3.ColorScheme as MaterialColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import io.github.xxtvrxx233.wearcompass.presentation.WearFonts
import com.materialkolor.dynamicColorScheme

val LocalThemeSeed = staticCompositionLocalOf { Color(0xFF4CD964) }

@Composable
fun CompassTheme(
    themePreferences: ThemePreferences,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dynamic = themePreferences.useDynamicColor.value
    val seed = themePreferences.seedColor.intValue
    val style = themePreferences.paletteStyle.value
    val specVersion = themePreferences.specVersion.value

    val resolvedSeed = remember(dynamic, seed) {
        if (dynamic) systemSeedColor(context) ?: seed else seed
    }
    val colors = remember(resolvedSeed, style, specVersion) {
        runCatching {
            dynamicColorScheme(
                seedColor = Color(resolvedSeed),
                isDark = true,
                style = style,
                specVersion = specVersion,
            ).toWearColorScheme()
        }.getOrElse { FallbackColorScheme }
    }

    CompositionLocalProvider(LocalThemeSeed provides Color(resolvedSeed)) {
        MaterialTheme(
            colorScheme = colors,
            typography = timeTypography(),
            content = content,
        )
    }
}

@Composable
private fun timeTypography(): Typography {
    val base = Typography()

    return base.copy(arcMedium = base.arcMedium.copy(fontFamily = WearFonts.medium))
}

private fun systemSeedColor(context: Context): Int? =
    runCatching {
        ContextCompat.getColor(context, android.R.color.system_accent1_500)
    }.getOrNull()

private fun MaterialColorScheme.toWearColorScheme(): ColorScheme = ColorScheme(
    primary = primaryFixed,
    primaryDim = primaryFixedDim,
    primaryContainer = primaryContainer,
    onPrimary = onPrimaryFixed,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondaryFixed,
    secondaryDim = secondaryFixedDim,
    secondaryContainer = secondaryContainer,
    onSecondary = onSecondaryFixed,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiaryFixed,
    tertiaryDim = tertiaryFixedDim,
    tertiaryContainer = tertiaryContainer,
    onTertiary = onTertiaryFixed,
    onTertiaryContainer = onTertiaryContainer,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    onSurface = onSurface,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    background = Color.Black,
    onBackground = onBackground,
    error = error,
    errorDim = errorContainer,
    errorContainer = errorContainer,
    onError = onError,
    onErrorContainer = onErrorContainer,
)

private val FallbackColorScheme: ColorScheme = ColorScheme().copy(
    primary = Color(0xFF4CD964),
    primaryDim = Color(0xFF3BB84F),
    tertiary = Color(0xFF8FE3A5),
)
