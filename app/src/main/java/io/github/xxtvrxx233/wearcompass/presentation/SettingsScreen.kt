package io.github.xxtvrxx233.wearcompass.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import io.github.xxtvrxx233.wearcompass.R
import io.github.xxtvrxx233.wearcompass.presentation.theme.ThemePreferences

@Composable
fun SettingsScreen(
    controller: CompassController,
    listState: TransformingLazyColumnState,
    themePreferences: ThemePreferences,
    isActive: Boolean,
    onOpenCalibration: () -> Unit,
    onOpenThemeColor: () -> Unit,
    onOpenAltitudeUnit: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenMagneticBaseline: () -> Unit,
    onToggleAltitudeCalibration: (Boolean) -> Unit,
    onToggleTrueNorth: (Boolean) -> Unit,
) {

    val useTrueNorth = controller.useTrueNorth.value
    val declination = controller.declinationDegrees.floatValue
    val needsCalibration = controller.needsCalibration.value
    val hint = controller.hint.value

    val transformationSpec = rememberTransformationSpec()

    val trueNorthSummary = when {
        !useTrueNorth -> stringResource(R.string.true_north_off)
        hint == CompassHint.NO_LOCATION_PERMISSION -> stringResource(R.string.location_needed)
        declination.isNaN() -> stringResource(R.string.locating)
        else -> stringResource(R.string.true_north_on, formatDeclination(declination))
    }
    val themeSummary = if (themePreferences.useDynamicColor.value) {
        stringResource(R.string.theme_follow_system_on)
    } else {
        stringResource(
            R.string.theme_custom_summary,
            String.format("#%06X", themePreferences.seedColor.intValue and 0xFFFFFF),
            paletteStyleLabel(themePreferences.paletteStyle.value),
        )
    }
    val calibrationSummary = if (needsCalibration) {
        stringResource(R.string.calibration_needed)
    } else {
        stringResource(R.string.calibration_summary)
    }

    ScreenScaffold(
        scrollState = listState,
        modifier = Modifier.wearRotaryScroll(listState, isActive),
        edgeButton = {},
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
                        Text(stringResource(R.string.settings_title))
                    }
                }
                item {
                    SwitchButton(
                        checked = useTrueNorth,
                        onCheckedChange = onToggleTrueNorth,
                        label = { Text(stringResource(R.string.true_north)) },
                        secondaryLabel = { Text(trueNorthSummary) },
                        icon = { Icon(Icons.Rounded.Explore, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
                item {
                    SwitchButton(
                        checked = controller.altitudeUseGpsCalibration.value,
                        onCheckedChange = onToggleAltitudeCalibration,
                        label = { Text(stringResource(R.string.altitude_gps)) },
                        secondaryLabel = {
                            Text(
                                text = stringResource(R.string.altitude_gps_summary),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        icon = { Icon(Icons.Rounded.Place, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
                item {
                    ListSubHeader(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text(stringResource(R.string.settings_appearance))
                    }
                }
                item {
                    SwitchButton(
                        checked = controller.levelIndicatorEnabled.value,
                        onCheckedChange = controller::setLevelIndicatorEnabled,
                        label = { Text(stringResource(R.string.level_indicator)) },
                        secondaryLabel = { Text(stringResource(R.string.level_indicator_summary)) },
                        icon = { Icon(Icons.Rounded.Star, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = onOpenAltitudeUnit,
                        label = { Text(stringResource(R.string.altitude_unit)) },
                        secondaryLabel = {
                            Text(
                                stringResource(
                                    if (controller.altitudeUnit.value == AltitudeUnit.FEET) {
                                        R.string.altitude_unit_feet
                                    } else {
                                        R.string.altitude_unit_meters
                                    },
                                ),
                            )
                        },
                        icon = { Icon(Icons.Rounded.List, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = onOpenThemeColor,
                        label = { Text(stringResource(R.string.settings_theme_color)) },
                        secondaryLabel = { Text(themeSummary) },
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
                    ListSubHeader(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text(stringResource(R.string.settings_sensor))
                    }
                }
                item {
                    val baseline = controller.baselineMicroTesla.floatValue
                    val baselineSummary = if (baseline.isNaN()) {
                        stringResource(
                            R.string.baseline_summary_default,
                            controller.interferenceEnterMax().roundToInt().toString(),
                        )
                    } else {
                        stringResource(
                            R.string.baseline_summary_set,
                            baseline.roundToInt().toString(),
                            controller.interferenceEnterMax().roundToInt().toString(),
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenMagneticBaseline,
                        label = { Text(stringResource(R.string.magnetic_baseline)) },
                        secondaryLabel = { Text(baselineSummary) },
                        icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
                item {

                    FilledTonalButton(
                        onClick = onOpenCalibration,
                        label = { Text(stringResource(R.string.manual_calibration)) },
                        secondaryLabel = { Text(calibrationSummary) },
                        icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
                item {
                    ListSubHeader(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text(stringResource(R.string.about_section))
                    }
                }
                item {
                    FilledTonalButton(
                        onClick = onOpenLicenses,
                        label = { Text(stringResource(R.string.open_source_licenses)) },
                        icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
        }
}

internal val ListExtraBottomPadding = 32.dp
