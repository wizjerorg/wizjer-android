package org.wizjer.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Roles: primary = buttons and your messages (brand teal); secondary = the mint accent used
// sparingly (current step, granted ✓, the logo's eye); secondaryContainer = soft mint tint.
private val LightColors = lightColorScheme(
    primary = BrandTeal,
    onPrimary = Paper,
    secondary = BrandMint,
    onSecondary = BrandTeal,
    secondaryContainer = MintContainer,
    onSecondaryContainer = BrandTeal,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceContainerLow = Paper,
    surfaceContainer = Tint,
    onSurfaceVariant = InkMuted,
    outlineVariant = Hairline,
)

private val DarkColors = darkColorScheme(
    primary = BrandMintLight,
    onPrimary = BrandTeal,
    secondary = MintDark,
    onSecondary = BrandTeal,
    secondaryContainer = MintContainerDark,
    onSecondaryContainer = OnMintContainerDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceContainerLow = PaperDark,
    surfaceContainer = TintDark,
    onSurfaceVariant = InkMutedDark,
    outlineVariant = HairlineDark,
)

/**
 * The app is light-only for now (the dark scheme is kept for a future toggle in Settings).
 * [textScale] enlarges all text on top of the system font size (the in-app "Text size" setting).
 */
@Composable
fun WizjerTheme(
    darkTheme: Boolean = false,
    textScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = WizjerTypography,
            content = content,
        )
    }
}
