package io.github.xxtvrxx233.wearcompass.presentation.theme

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

class ThemePreferences(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val useDynamicColor = mutableStateOf(preferences.getBoolean(KEY_DYNAMIC, true))
    val seedColor = mutableIntStateOf(preferences.getInt(KEY_SEED, DEFAULT_SEED_COLOR))
    val paletteStyle = mutableStateOf(
        runCatching {
            PaletteStyle.valueOf(preferences.getString(KEY_STYLE, PaletteStyle.TonalSpot.name)!!)
        }.getOrDefault(PaletteStyle.TonalSpot),
    )
    val specVersion = mutableStateOf(
        runCatching {
            ColorSpec.SpecVersion.valueOf(
                preferences.getString(KEY_SPEC, ColorSpec.SpecVersion.SPEC_2021.name)!!,
            )
        }.getOrDefault(ColorSpec.SpecVersion.SPEC_2021),
    )

    fun setDynamicColor(dynamic: Boolean) {
        useDynamicColor.value = dynamic
        preferences.edit().putBoolean(KEY_DYNAMIC, dynamic).apply()
    }

    fun setSeedColor(color: Int) {
        seedColor.intValue = color
        preferences.edit().putInt(KEY_SEED, color).apply()
    }

    fun selectColor(color: Int) {
        setSeedColor(color)
        setDynamicColor(false)
    }

    fun setPaletteStyle(style: PaletteStyle) {
        paletteStyle.value = style
        preferences.edit().putString(KEY_STYLE, style.name).apply()
    }

    fun setSpecVersion(version: ColorSpec.SpecVersion) {
        specVersion.value = version
        preferences.edit().putString(KEY_SPEC, version.name).apply()
    }

    private companion object {
        const val PREFS_NAME = "compass_theme"
        const val KEY_DYNAMIC = "use_dynamic_color"
        const val KEY_SEED = "seed_color"
        const val KEY_STYLE = "palette_style"
        const val KEY_SPEC = "color_spec"

        const val DEFAULT_SEED_COLOR = 0xFF4CD964.toInt()
    }
}
