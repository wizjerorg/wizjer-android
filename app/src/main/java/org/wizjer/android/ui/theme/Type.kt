package org.wizjer.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import org.wizjer.android.R

/** Manrope (variable weight, SIL OFL; license in media/fonts). Covers Polish and Ukrainian. */
val WizjerFont = FontFamily(
    listOf(400, 500, 600, 700, 800).map { weight ->
        Font(
            R.font.manrope,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    }
)

internal val WizjerTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = WizjerFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1).sp,
        lineBreak = LineBreak.Heading,
    ),
    headlineMedium = TextStyle(
        fontFamily = WizjerFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.9).sp,
        lineBreak = LineBreak.Heading,
    ),
    headlineSmall = TextStyle(
        fontFamily = WizjerFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.6).sp,
        lineBreak = LineBreak.Heading,
    ),
    titleMedium = TextStyle(
        fontFamily = WizjerFont,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = WizjerFont,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.2).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = WizjerFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.05).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = WizjerFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.1).sp,
    ),
    labelLarge = TextStyle(
        fontFamily = WizjerFont,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    ),
    labelMedium = TextStyle(
        fontFamily = WizjerFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
    ),
)
