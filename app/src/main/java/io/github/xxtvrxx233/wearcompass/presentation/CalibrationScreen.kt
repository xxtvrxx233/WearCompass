package io.github.xxtvrxx233.wearcompass.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import io.github.xxtvrxx233.wearcompass.R
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

private const val REQUIRED_ROTATION_DEGREES = 720f

private const val MIN_STEP_DEGREES = 0.4f

private const val MAX_STEP_DEGREES = 25f

private const val HAPTIC_STEP_DEGREES = 90f

private const val SAMPLE_INTERVAL_MILLIS = 100L

@Composable
fun CalibrationScreen(
    controller: CompassController,
    isActive: Boolean,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val chargingInterference = controller.chargingInterference.value
    var turnedDegrees by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(controller) {
        var lastHeading = controller.targetAzimuth.floatValue
        var nextHapticAt = HAPTIC_STEP_DEGREES
        while (true) {
            delay(SAMPLE_INTERVAL_MILLIS)

            if (controller.chargingInterference.value) continue

            val heading = controller.targetAzimuth.floatValue
            var delta = heading - lastHeading
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            lastHeading = heading

            val step = abs(delta)
            if (step < MIN_STEP_DEGREES || step > MAX_STEP_DEGREES) continue

            turnedDegrees = (turnedDegrees + step).coerceAtMost(REQUIRED_ROTATION_DEGREES)
            if (turnedDegrees >= nextHapticAt) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                nextHapticAt += HAPTIC_STEP_DEGREES
            }
        }
    }

    val fieldMicroTesla = controller.fieldMicroTesla.floatValue
    val interference = controller.magneticInterference.value
    val fieldValue = String.format(Locale.getDefault(), "%.1f", fieldMicroTesla)
    val fieldText = when {
        chargingInterference -> stringResource(R.string.calibration_charger_warning)
        fieldMicroTesla.isNaN() -> stringResource(R.string.field_unknown)
        interference -> stringResource(R.string.field_interference, fieldValue)
        else -> stringResource(R.string.field_strength, fieldValue)
    }
    val completed = turnedDegrees >= REQUIRED_ROTATION_DEGREES

    ScreenScaffold(
        scrollState = listState,
        modifier = Modifier.wearRotaryScroll(listState, isActive),
        edgeButton = {
            EdgeButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    Text(stringResource(R.string.calibration_title))
                }
            }
            item {
                Figure8Animation(
                    completed = completed,
                    modifier = Modifier.fillMaxWidth().height(86.dp).padding(horizontal = 24.dp),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.calibration_instruction),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    text = if (completed) {
                        stringResource(R.string.calibration_finished)
                    } else {
                        stringResource(
                            R.string.calibration_progress,
                            turnedDegrees.toInt(),
                            REQUIRED_ROTATION_DEGREES.toInt(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (completed) {
                        Color(0xFF8FE3A5)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            item {
                Text(
                    text = fieldText,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {

                        chargingInterference -> MaterialTheme.colorScheme.error
                        interference -> Color(0xFFFFB020)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (completed) {
                item {
                    Button(
                        onClick = onBack,
                        label = { Text(stringResource(R.string.calibration_done)) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
        }
    }

}

@Composable
private fun Figure8Animation(completed: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "figure-8")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "figure-8-phase",
    )
    val accent = if (completed) Color(0xFF4CD964) else Color.White.copy(alpha = 0.75f)

    Canvas(modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val amplitude = min(size.width, size.height) * 0.38f
        val steps = 120

        val path = Path()
        for (i in 0..steps) {
            val t = i / steps.toFloat() * 2f * PI.toFloat()
            val x = centerX + amplitude * sin(t)
            val y = centerY + amplitude * 0.60f * sin(2f * t)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color.White.copy(alpha = 0.22f), style = Stroke(width = 2.dp.toPx()))

        val t = phase * 2f * PI.toFloat()
        val dotX = centerX + amplitude * sin(t)
        val dotY = centerY + amplitude * 0.60f * sin(2f * t)
        drawCircle(accent, radius = 5.dp.toPx(), center = Offset(dotX, dotY))
    }
}
