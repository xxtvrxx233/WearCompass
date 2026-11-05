package io.github.xxtvrxx233.wearcompass.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import io.github.xxtvrxx233.wearcompass.R
import java.util.Locale
import kotlin.math.roundToInt

private val MultiplierOptions = listOf(1.5f, 2f, 3f)

@Composable
internal fun MagneticBaselineScreen(
    controller: CompassController,
    listState: TransformingLazyColumnState,
    isActive: Boolean,
    onBack: () -> Unit,
) {
    val transformationSpec = rememberTransformationSpec()

    val field = controller.fieldMicroTesla.floatValue
    val baseline = controller.baselineMicroTesla.floatValue
    val multiplier = controller.thresholdMultiplier.floatValue
    val sampling = controller.isSamplingBaseline.value
    val progress = controller.baselineSamplingProgress.floatValue
    val charging = controller.isCharging.value

    val fieldText = if (field.isNaN()) "—" else String.format(Locale.getDefault(), "%.1f", field)
    val summary = if (baseline.isNaN()) {
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
                    Text(stringResource(R.string.magnetic_baseline))
                }
            }
            item {
                Text(
                    text = stringResource(R.string.baseline_current, fieldText),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    text = summary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item {
                FilledTonalButton(
                    onClick = controller::startBaselineSampling,
                    enabled = !sampling && !charging,
                    label = {
                        Text(
                            text = if (sampling) {
                                stringResource(R.string.baseline_sampling, (progress * 100).roundToInt())
                            } else {
                                stringResource(R.string.baseline_sample)
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item {

                Text(
                    text = if (charging) {
                        stringResource(R.string.baseline_charging_hint)
                    } else {
                        stringResource(R.string.baseline_hint)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = if (charging) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            item {
                ListSubHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(stringResource(R.string.baseline_multiplier))
                }
            }
            MultiplierOptions.forEach { option ->
                item(key = "multiplier-$option") {
                    RadioButton(
                        selected = multiplier == option,
                        onSelect = { controller.setThresholdMultiplier(option) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        contentPadding = ButtonDefaults.ContentPadding,
                        label = {
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f×", option),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
            if (!baseline.isNaN()) {
                item {
                    FilledTonalButton(
                        onClick = controller::clearBaseline,
                        label = { Text(stringResource(R.string.baseline_reset)) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
        }
    }
}
