package io.github.xxtvrxx233.wearcompass.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedAlignment
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.basicCurvedText
import androidx.wear.compose.foundation.curvedRow
import androidx.wear.compose.foundation.sizeIn
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.xxtvrxx233.wearcompass.R
import io.github.xxtvrxx233.wearcompass.data.normalizeDegrees
import java.util.Locale
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

private const val SMOOTHING_TAU_SECONDS = 0.05f

private const val LEVEL_DEGREES = 1.5f

private const val LEVEL_REARM_DEGREES = 4f

private const val CONVERGED_EPSILON_DEGREES = 0.02f

private fun shortestAngle(from: Float, to: Float): Float {
    var delta = to - from
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}

private val WarnAmber = Color(0xFFFFB020)
private val MutedWhite = Color(0xFFB8B8BE)

@Composable
fun CompassScreen(
    controller: CompassController,
    isActive: Boolean,
) {
    val directions = listOf(
        stringResource(R.string.direction_n),
        stringResource(R.string.direction_ne),
        stringResource(R.string.direction_e),
        stringResource(R.string.direction_se),
        stringResource(R.string.direction_s),
        stringResource(R.string.direction_sw),
        stringResource(R.string.direction_w),
        stringResource(R.string.direction_nw),
    )

    val sensorAvailable = controller.sensorAvailable.value
    val hint = controller.hint.value
    val needsCalibration = controller.needsCalibration.value
    val magneticInterference = controller.magneticInterference.value
    val chargingInterference = controller.chargingInterference.value
    val useTrueNorth = controller.useTrueNorth.value
    val declination = controller.declinationDegrees.floatValue

    val infoText: String
    val infoColor: Color
    when {
        !sensorAvailable -> {
            infoText = stringResource(R.string.no_compass_sensor)
            infoColor = WarnAmber
        }
        hint == CompassHint.NO_LOCATION_PERMISSION -> {
            infoText = stringResource(R.string.location_needed)
            infoColor = WarnAmber
        }
        chargingInterference -> {
            infoText = stringResource(R.string.disconnect_charger)
            infoColor = WarnAmber
        }
        magneticInterference -> {
            infoText = stringResource(R.string.magnetic_interference)
            infoColor = WarnAmber
        }
        needsCalibration -> {
            infoText = stringResource(R.string.calibrate)
            infoColor = WarnAmber
        }
        hint == CompassHint.LOCATING || (useTrueNorth && declination.isNaN()) -> {
            infoText = stringResource(R.string.locating)
            infoColor = MutedWhite
        }
        useTrueNorth -> {
            infoText = stringResource(R.string.north_true_declination, formatDeclination(declination))
            infoColor = MaterialTheme.colorScheme.primary
        }
        else -> {
            infoText = stringResource(R.string.north_magnetic)
            infoColor = MutedWhite
        }
    }

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(controller, isActive, controller.levelIndicatorEnabled.value) {
        if (!isActive || !controller.levelIndicatorEnabled.value) return@LaunchedEffect
        var armed = true
        snapshotFlow { controller.tiltDegrees.floatValue }.collect { tilt ->
            if (tilt > LEVEL_REARM_DEGREES) armed = true
            if (armed && tilt <= LEVEL_DEGREES) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                armed = false
            }
        }
    }

    LaunchedEffect(controller, isActive) {
        if (!isActive) {

            snapshotFlow { controller.targetAzimuth.floatValue }.collect { target ->
                controller.displayedAzimuth.floatValue = target
                controller.updateShownDegrees()
            }
            return@LaunchedEffect
        }

        var lastFrameNanos = 0L
        while (true) {
            snapshotFlow {
                shortestAngle(controller.displayedAzimuth.floatValue, controller.targetAzimuth.floatValue)
            }.first { abs(it) > CONVERGED_EPSILON_DEGREES }

            var animating = true
            while (animating) {
                withFrameNanos { frameNanos ->
                    val dt = if (lastFrameNanos == 0L) {
                        0f
                    } else {
                        ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    }
                    lastFrameNanos = frameNanos

                    val target = controller.targetAzimuth.floatValue
                    val current = controller.displayedAzimuth.floatValue
                    val delta = shortestAngle(current, target)
                    if (abs(delta) <= CONVERGED_EPSILON_DEGREES) {
                        controller.displayedAzimuth.floatValue = normalizeDegrees(target)
                        controller.updateShownDegrees()
                        animating = false
                    } else {
                        val factor = if (dt <= 0f) {
                            1f
                        } else {
                            (1.0 - exp(-dt / SMOOTHING_TAU_SECONDS)).toFloat()
                        }
                        controller.displayedAzimuth.floatValue = normalizeDegrees(current + delta * factor)
                        controller.updateShownDegrees()
                    }
                }
            }
            lastFrameNanos = 0L
        }
    }

    CompassScreenFrame(
        directions = directions,
        infoText = infoText,
        infoColor = infoColor,
        heading = { controller.currentHeading() },
        tilt = { controller.tiltDegrees.floatValue },
        degrees = { controller.shownDegrees.intValue },
        altitude = { controller.altitudeMeters.floatValue },
        altitudeUnit = controller.altitudeUnit.value,
        levelEnabled = controller.levelIndicatorEnabled.value,
        immersive = controller.immersiveMode.value,
    )
}

@Composable
internal fun CompassScreenFrame(
    directions: List<String>,
    infoText: String,
    infoColor: Color,
    heading: () -> Float,
    tilt: () -> Float,
    degrees: () -> Int,
    altitude: () -> Float = { Float.NaN },
    altitudeUnit: AltitudeUnit = AltitudeUnit.METERS,
    levelEnabled: Boolean = true,
    immersive: Boolean = false,
) {
    val a11yTemplate = stringResource(R.string.a11y_heading_format)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics {
                val shown = degrees()
                contentDescription = String.format(
                    Locale.getDefault(),
                    a11yTemplate,
                    shown,
                    directions[directionIndex(shown.toFloat())],
                )
            },
    ) {
        CompassDialFrame(
            directions = directions,
            heading = heading,
            tilt = tilt,
            degrees = degrees,
            levelEnabled = levelEnabled,
            immersive = immersive,
            modifier = Modifier.fillMaxSize(),
        )

        val altitudeTemplate = stringResource(R.string.altitude_display)
        val meters = altitude()
        if (!immersive && !meters.isNaN()) {
            val feet = altitudeUnit == AltitudeUnit.FEET

            val value = if (feet) meters * 3.28084f else meters
            val unit = if (feet) "ft" else "m"
            SideCurvedText(
                text = altitudeTemplate.format("${value.roundToInt()} $unit"),
                anchor = LEFT_ANCHOR_DEGREES,
            )
        }

        if (!immersive && levelEnabled) {
            val tiltTemplate = stringResource(R.string.tilt_display)
            val tiltDegrees by remember { derivedStateOf { tilt().roundToInt() } }
            SideCurvedText(
                text = tiltTemplate.format(tiltDegrees),
                anchor = RIGHT_ANCHOR_DEGREES,
            )
        }

        if (!immersive) {
            CurvedLayout(
                modifier = Modifier.fillMaxSize(),
                anchor = BOTTOM_ANCHOR_DEGREES,

                angularDirection = CurvedDirection.Angular.CounterClockwise,
            ) {
                curvedRow(
                    modifier = CurvedModifier.sizeIn(maxSweepDegrees = STATUS_MAX_SWEEP_DEGREES),
                    radialAlignment = CurvedAlignment.Radial.Center,
                ) {
                    basicCurvedText(infoText) {
                        CurvedTextStyle(

                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
                            fontSize = 13.5.sp,
                            fontFamily = WearFonts.medium,
                        )
                    }
                }
            }
        }
    }

}

private const val BOTTOM_ANCHOR_DEGREES = 90f
private const val LEFT_ANCHOR_DEGREES = 180f
private const val RIGHT_ANCHOR_DEGREES = 0f

@Composable
private fun SideCurvedText(text: String, anchor: Float) {
    CurvedLayout(
        modifier = Modifier.fillMaxSize(),
        anchor = anchor,
    ) {
        curvedRow(
            modifier = CurvedModifier.sizeIn(maxSweepDegrees = 60f),
            radialAlignment = CurvedAlignment.Radial.Center,
        ) {
            basicCurvedText(text) {
                CurvedTextStyle(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontFamily = WearFonts.medium,
                )
            }
        }
    }
}
private const val STATUS_MAX_SWEEP_DEGREES = 100f

private val PreviewDirections = listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")

@Preview(name = "整屏 · 磁北", device = "spec:width=384px,height=384px,dpi=320", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CompassScreenPreview() {
    CompassScreenFrame(
        directions = PreviewDirections,
        infoText = "磁北",
        infoColor = MutedWhite,
        heading = { 90f },
        tilt = { 1f },
        degrees = { 90 },
    )
}

@Preview(name = "整屏 · 磁场异常", device = "spec:width=384px,height=384px,dpi=320", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CompassScreenPreviewWarning() {
    CompassScreenFrame(
        directions = PreviewDirections,
        infoText = "磁场异常",
        infoColor = WarnAmber,
        heading = { 32f },
        tilt = { 20f },
        degrees = { 32 },
    )
}
