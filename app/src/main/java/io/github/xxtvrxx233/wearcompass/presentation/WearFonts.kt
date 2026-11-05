package io.github.xxtvrxx233.wearcompass.presentation

import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import java.io.File

internal object WearFonts {

    private val googleSansFlex = File("/product/fonts/Wear-GoogleSansFlex-Regular.ttf")
    private val googleSansStatic = File("/product/fonts/Wear-GoogleSans-Regular.ttf")
    private val robotoFlex = File("/system/fonts/RobotoFlex-Regular.ttf")

    val light: FontFamily = family(300)
    val regular: FontFamily = family(400)
    val medium: FontFamily = family(540)

    private fun family(weight: Int): FontFamily {
        val fromVariable = runCatching {
            if (googleSansFlex.exists()) {
                Typeface.Builder(googleSansFlex)
                    .setFontVariationSettings("'wght' $weight")
                    .build()
            } else {
                null
            }
        }.getOrNull()

        val fromStatic = fromVariable ?: runCatching {
            if (googleSansStatic.exists()) Typeface.Builder(googleSansStatic).build() else null
        }.getOrNull()

        val fallback = fromStatic ?: runCatching {
            if (robotoFlex.exists()) {
                Typeface.Builder(robotoFlex).setFontVariationSettings("'wght' $weight").build()
            } else {
                null
            }
        }.getOrNull() ?: Typeface.DEFAULT

        if (weight == 400) {
            Log.d(
                "WearFonts",
                "source=${when {
                    fromVariable != null -> "google-sans-flex"
                    fromStatic != null -> "google-sans-static"
                    else -> "fallback"
                }} typeface=$fallback",
            )
        }
        return FontFamily(fallback)
    }
}
