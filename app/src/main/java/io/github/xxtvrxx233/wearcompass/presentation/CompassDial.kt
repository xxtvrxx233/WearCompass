package io.github.xxtvrxx233.wearcompass.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal object DialDesign {

    const val tickOuter = 0.835f
    const val tickMinorLength = 0.060f
    const val tickMediumLength = 0.090f
    const val tickMajorLength = 0.105f

    const val letterRadius = 0.620f
    const val letterFontSize = 0.140f

    const val discRadius = 0.515f

    const val degreeFontSize = 0.340f
    const val degreeSignFontSize = 0.170f
    const val directionFontSize = 0.105f

    const val indexOuter = 0.835f
    const val indexInner = 0.745f
    const val indexHalfWidth = 0.012f

    const val levelRangeDegrees = 12f

    const val discLevelAlpha = 0.95f
}

@Composable
internal fun CompassDialFrame(
    directions: List<String>,
    heading: () -> Float,
    tilt: () -> Float,
    degrees: () -> Int,
    levelEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val density = LocalDensity.current

    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary

    BoxWithConstraints(modifier.fillMaxSize()) {
        val radiusPx = with(density) { min(constraints.maxWidth, constraints.maxHeight) / 2f }
        val styles = remember(radiusPx, density) { createStyles(radiusPx, density) }
        val cardinalLayouts = remember(styles, textMeasurer, accent) {
            measureCardinals(textMeasurer, styles, accent)
        }
        val directionLayouts = remember(styles, directions, textMeasurer) {
            directions.map { textMeasurer.measure(it, styles.direction) }
        }
        val indexPath = remember(radiusPx) {
            val halfWidth = radiusPx * DialDesign.indexHalfWidth
            Path().apply {
                moveTo(-halfWidth, -radiusPx * DialDesign.indexOuter)
                lineTo(halfWidth, -radiusPx * DialDesign.indexOuter)
                lineTo(halfWidth, -radiusPx * DialDesign.indexInner)
                lineTo(-halfWidth, -radiusPx * DialDesign.indexInner)
                close()
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val currentHeading = heading()

            val levelContentColor = if (accent.luminance() > 0.5f) Color.Black else onAccent
            val levelness = if (levelEnabled) {
                (1f - tilt() / DialDesign.levelRangeDegrees).coerceIn(0f, 1f)
            } else {
                0f
            }

            drawRect(Color.Black)

            drawTicks(center, radius, currentHeading)

            val labelRadius = radius * DialDesign.letterRadius
            cardinalLayouts.forEach { (bearing, layout) ->
                val theta = Math.toRadians((bearing - currentHeading).toDouble())
                val x = center.x + labelRadius * sin(theta).toFloat()
                val y = center.y - labelRadius * cos(theta).toFloat()
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f),
                )
            }

            val discAlpha = (levelness * 1.8f).coerceIn(0f, 1f) * DialDesign.discLevelAlpha
            if (discAlpha > 0.01f) {
                drawCircle(
                    color = accent.copy(alpha = discAlpha),
                    radius = radius * DialDesign.discRadius,
                    center = center,
                )
            }

            translate(left = center.x, top = center.y) {
                drawPath(indexPath, accent)
            }

            drawReadout(
                center = center,
                radius = radius,
                degrees = degrees(),
                directionLayouts = directionLayouts,
                styles = styles,
                textMeasurer = textMeasurer,

                contentColor = lerp(
                    Color.White,
                    levelContentColor,
                    ((levelness - 0.12f) / 0.18f).coerceIn(0f, 1f),
                ),
            )
        }
    }
}

@Composable
fun CompassDial(
    controller: CompassController,
    directions: List<String>,
    modifier: Modifier = Modifier,
) {
    CompassDialFrame(
        directions = directions,
        heading = { controller.currentHeading() },
        tilt = { controller.tiltDegrees.floatValue },
        degrees = { controller.shownDegrees.intValue },
        levelEnabled = controller.levelIndicatorEnabled.value,
        modifier = modifier,
    )
}

private class DialStyles(
    val cardinal: TextStyle,
    val degrees: TextStyle,
    val degreeSign: TextStyle,
    val direction: TextStyle,
)

private fun createStyles(radiusPx: Float, density: Density): DialStyles {
    fun size(fraction: Float) = with(density) { (radiusPx * fraction).toSp() }
    return DialStyles(
        cardinal = TextStyle(
            fontFamily = WearFonts.medium,
            fontSize = size(DialDesign.letterFontSize),
            color = Color.White,
        ),
        degrees = TextStyle(
            fontFamily = WearFonts.regular,
            fontSize = size(DialDesign.degreeFontSize),
            color = Color.White,
            fontFeatureSettings = "tnum",
        ),
        degreeSign = TextStyle(
            fontFamily = WearFonts.regular,
            fontSize = size(DialDesign.degreeSignFontSize),
            color = Color.White.copy(alpha = 0.9f),
        ),
        direction = TextStyle(
            fontFamily = WearFonts.medium,
            fontSize = size(DialDesign.directionFontSize),
            color = Color.White.copy(alpha = 0.75f),
        ),
    )
}

private fun measureCardinals(
    textMeasurer: TextMeasurer,
    styles: DialStyles,
    accent: Color,
): List<Pair<Int, TextLayoutResult>> {
    val labels = listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
    return labels.map { (bearing, text) ->
        val style = if (bearing == 0) {
            styles.cardinal.copy(color = accent)
        } else {
            styles.cardinal.copy(color = Color.White.copy(alpha = 0.88f))
        }
        bearing to textMeasurer.measure(text, style)
    }
}

private fun DrawScope.drawTicks(center: Offset, radius: Float, heading: Float) {
    val outer = radius * DialDesign.tickOuter
    for (bearing in 0 until 360 step 15) {
        var screenAngle = bearing - heading
        if (screenAngle > 180f) screenAngle -= 360f
        if (screenAngle < -180f) screenAngle += 360f

        val level = when {
            bearing % 90 == 0 -> 3
            bearing % 45 == 0 -> 2
            else -> 1
        }
        val inner = when (level) {
            3 -> outer - radius * DialDesign.tickMajorLength
            2 -> outer - radius * DialDesign.tickMediumLength
            else -> outer - radius * DialDesign.tickMinorLength
        }
        val color = when (level) {
            3 -> Color.White.copy(alpha = 0.96f)
            2 -> Color.White.copy(alpha = 0.60f)
            else -> Color.White.copy(alpha = 0.32f)
        }
        val width = when (level) {
            3 -> 2.6.dp.toPx()
            2 -> 2.0.dp.toPx()
            else -> 1.6.dp.toPx()
        }
        val theta = Math.toRadians(screenAngle.toDouble())
        val sinTheta = sin(theta).toFloat()
        val cosTheta = cos(theta).toFloat()
        drawLine(
            color = color,
            start = Offset(center.x + outer * sinTheta, center.y - outer * cosTheta),
            end = Offset(center.x + inner * sinTheta, center.y - inner * cosTheta),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawReadout(
    center: Offset,
    radius: Float,
    degrees: Int,
    directionLayouts: List<TextLayoutResult>,
    styles: DialStyles,
    textMeasurer: TextMeasurer,
    contentColor: Color,
) {
    val digitsLayout = textMeasurer.measure(degrees.toString(), styles.degrees)
    val signLayout = textMeasurer.measure("°", styles.degreeSign)
    val directionLayout = directionLayouts[directionIndex(degrees.toFloat())]

    val signGap = radius * 0.004f
    val totalWidth = digitsLayout.size.width + signGap + signLayout.size.width
    val digitsTop = center.y - digitsLayout.size.height / 2f - radius * 0.055f
    val left = center.x - totalWidth / 2f

    drawText(digitsLayout, color = contentColor, topLeft = Offset(left, digitsTop))
    drawText(
        signLayout,
        color = contentColor.copy(alpha = 0.85f),
        topLeft = Offset(
            left + digitsLayout.size.width + signGap,
            digitsTop + digitsLayout.size.height * 0.02f,
        ),
    )
    drawText(
        directionLayout,
        color = contentColor.copy(alpha = 0.85f),
        topLeft = Offset(
            center.x - directionLayout.size.width / 2f,
            digitsTop + digitsLayout.size.height + radius * 0.005f,
        ),
    )
}

fun directionIndex(heading: Float): Int = (((heading + 22.5f) / 45f).toInt()) % 8
