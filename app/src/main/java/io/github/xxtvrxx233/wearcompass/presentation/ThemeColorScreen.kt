package io.github.xxtvrxx233.wearcompass.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconToggleButton
import androidx.wear.compose.material3.IconToggleButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import io.github.xxtvrxx233.wearcompass.R
import io.github.xxtvrxx233.wearcompass.presentation.theme.ThemePreferences

enum class ThemeSubPage {
    PaletteStyle,
    ColorSpec,
}

private val SwatchColors = listOf(
    0xFFFF9FCAFF, 0xFFBEC2FF, 0xFFD6BBFF,
    0xFFBF8D9D, 0xFFE6ACCC, 0xFFFFB3A5,
    0xFFFFD166, 0xFFE9EF80, 0xFF9ACB78,
    0xFF75CBBA, 0xFF44CBE0, 0xFFB5C4CD,
).map { Color(it) }

@Composable
internal fun ThemeChoicePage(
    subPage: ThemeSubPage,
    themePreferences: ThemePreferences,
    listState: TransformingLazyColumnState,
    isActive: Boolean,
    onBack: () -> Unit,
) {
    when (subPage) {
        ThemeSubPage.PaletteStyle -> ChoicePage(
            title = stringResource(R.string.theme_palette_style),
            choices = PaletteStyle.entries.map { it.name to paletteStyleLabel(it) },
            selected = themePreferences.paletteStyle.value.name,
            onChoose = { themePreferences.setPaletteStyle(PaletteStyle.valueOf(it)) },
            listState = listState,
            isActive = isActive,
            onBack = onBack,
        )

        ThemeSubPage.ColorSpec -> ChoicePage(
            title = stringResource(R.string.theme_color_spec),
            choices = ColorSpec.SpecVersion.entries.map { it.name to specVersionLabel(it) },
            selected = themePreferences.specVersion.value.name,
            onChoose = { themePreferences.setSpecVersion(ColorSpec.SpecVersion.valueOf(it)) },
            listState = listState,
            isActive = isActive,
            onBack = onBack,
        )
    }
}

@Composable
internal fun ThemeColorMainPage(
    themePreferences: ThemePreferences,
    listState: TransformingLazyColumnState,
    isActive: Boolean,
    onOpenSubPage: (ThemeSubPage) -> Unit,
    onBack: () -> Unit,
) {
    val transformationSpec = rememberTransformationSpec()
    val dynamic = themePreferences.useDynamicColor.value
    val seed = Color(themePreferences.seedColor.intValue)
    val style = themePreferences.paletteStyle.value
    val specVersion = themePreferences.specVersion.value

    ScreenScaffold(
        scrollState = listState,
        modifier = Modifier.wearRotaryScroll(listState, isActive),
        edgeButton = {
            EdgeButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
    ) { contentPadding ->
        val layoutDirection = LocalLayoutDirection.current
        TransformingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + ListExtraBottomPadding,
            ),
        ) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(stringResource(R.string.theme_color_title))
                }
            }
            item {
                SwitchButton(
                    checked = dynamic,
                    onCheckedChange = themePreferences::setDynamicColor,
                    label = { Text(stringResource(R.string.theme_follow_system)) },
                    secondaryLabel = {
                        Text(
                            stringResource(
                                if (dynamic) R.string.theme_follow_system_on else R.string.theme_follow_system_off,
                            ),
                        )
                    },
                    icon = {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item {
                FilledTonalButton(
                    onClick = { onOpenSubPage(ThemeSubPage.PaletteStyle) },
                    label = { Text(stringResource(R.string.theme_palette_style)) },
                    secondaryLabel = { Text(paletteStyleLabel(style)) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item {
                FilledTonalButton(
                    onClick = { onOpenSubPage(ThemeSubPage.ColorSpec) },
                    label = { Text(stringResource(R.string.theme_color_spec)) },
                    secondaryLabel = { Text(specVersionLabel(specVersion)) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item {
                ListSubHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(stringResource(R.string.theme_swatches))
                }
            }
            SwatchColors.chunked(3).forEachIndexed { rowIndex, row ->
                item(key = "swatch-row-$rowIndex") {

                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        row.forEach { color ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                ColorSwatch(
                                    color = color,
                                    selected = !dynamic && seed == color,
                                    onClick = { themePreferences.selectColor(color.toArgb()) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
internal fun ChoicePage(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onChoose: (String) -> Unit,
    listState: TransformingLazyColumnState,
    isActive: Boolean,
    onBack: () -> Unit,
) {
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
        modifier = Modifier.wearRotaryScroll(listState, isActive),
        edgeButton = {
            EdgeButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
    ) { contentPadding ->
        val layoutDirection = LocalLayoutDirection.current
        TransformingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + ListExtraBottomPadding,
            ),
        ) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(title)
                }
            }
            choices.forEach { (value, label) ->
                item(key = "choice-$value") {
                    RadioButton(
                        selected = value == selected,
                        onSelect = { onChoose(value) },
                        modifier = Modifier.fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        contentPadding = ButtonDefaults.ContentPadding,
                        label = { Text(label, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }

}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val checkTint = if (color.luminance() > 0.45f) Color.Black else Color.White
    IconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = Modifier.fillMaxWidth(0.9f).heightIn(min = 48.dp),
        colors = IconToggleButtonDefaults.colors(
            checkedContainerColor = color,
            checkedContentColor = checkTint,
            uncheckedContainerColor = color,
            uncheckedContentColor = Color.Transparent,
        ),
        shapes = IconToggleButtonDefaults.variantAnimatedShapes(),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,

            tint = if (selected) checkTint else Color.Transparent,
        )
    }
}

@Composable
internal fun paletteStyleLabel(style: PaletteStyle): String = stringResource(
    when (style) {
        PaletteStyle.TonalSpot -> R.string.palette_tonal_spot
        PaletteStyle.Neutral -> R.string.palette_neutral
        PaletteStyle.Vibrant -> R.string.palette_vibrant
        PaletteStyle.Expressive -> R.string.palette_expressive
        PaletteStyle.Rainbow -> R.string.palette_rainbow
        PaletteStyle.FruitSalad -> R.string.palette_fruit_salad
        PaletteStyle.Monochrome -> R.string.palette_monochrome
        PaletteStyle.Fidelity -> R.string.palette_fidelity
        PaletteStyle.Content -> R.string.palette_content
    },
)

@Composable
internal fun specVersionLabel(version: ColorSpec.SpecVersion): String = stringResource(
    when (version) {
        ColorSpec.SpecVersion.SPEC_2021 -> R.string.spec_2021
        ColorSpec.SpecVersion.SPEC_2025 -> R.string.spec_2025
    },
)
