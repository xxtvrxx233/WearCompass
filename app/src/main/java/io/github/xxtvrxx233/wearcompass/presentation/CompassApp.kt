package io.github.xxtvrxx233.wearcompass.presentation

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import io.github.xxtvrxx233.wearcompass.R
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.TimeText
import io.github.xxtvrxx233.wearcompass.data.normalizeDegrees
import io.github.xxtvrxx233.wearcompass.presentation.theme.ThemePreferences
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class OverlayRoute {
    Calibration,
    ThemeColor,
    AltitudeUnit,
    Licenses,
    MagneticBaseline,
}

@Composable
fun CompassApp(
    controller: CompassController,
    themePreferences: ThemePreferences,
) {
    val pagerState = rememberPagerState(pageCount = { TAB_COUNT })
    val scope = rememberCoroutineScope()
    val settingsListState = rememberTransformingLazyColumnState()
    val themeListState = rememberTransformingLazyColumnState()
    val choiceListState = rememberTransformingLazyColumnState()
    val unitListState = rememberTransformingLazyColumnState()
    val licensesListState = rememberTransformingLazyColumnState()
    val licenseDetailListState = rememberTransformingLazyColumnState()
    val baselineListState = rememberTransformingLazyColumnState()

    var overlayRoute by rememberSaveable { mutableStateOf<OverlayRoute?>(null) }
    var themeSubPage by rememberSaveable { mutableStateOf<ThemeSubPage?>(null) }
    var licenseDetailIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val hasSubLayer = themeSubPage != null || licenseDetailIndex != null

    val overlaySlide = remember { Animatable(0f) }
    val subSlide = remember { Animatable(0f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> controller.onLocationPermissionResult(granted) }

    val altitudePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> controller.onAltitudePermissionResult(granted) }

    fun openOverlay(route: OverlayRoute) {
        if (overlayRoute != null || overlaySlide.isRunning) return
        themeSubPage = null
        overlayRoute = route
        scope.launch {
            overlaySlide.snapTo(1f)
            overlaySlide.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium))
        }
    }

    fun closeOverlay() {
        if (overlayRoute == null || overlaySlide.isRunning) return
        scope.launch {
            overlaySlide.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
            overlayRoute = null
            themeSubPage = null
            licenseDetailIndex = null
            overlaySlide.snapTo(0f)
        }
    }

    fun openLicenseDetail(index: Int) {
        if (hasSubLayer || subSlide.isRunning) return
        licenseDetailIndex = index
        scope.launch {
            subSlide.snapTo(1f)
            subSlide.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium))
        }
    }

    fun openSubPage(page: ThemeSubPage) {
        if (hasSubLayer || subSlide.isRunning) return
        themeSubPage = page
        scope.launch {
            subSlide.snapTo(1f)
            subSlide.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium))
        }
    }

    fun closeSubLayer() {
        if (!hasSubLayer || subSlide.isRunning) return
        scope.launch {
            subSlide.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
            themeSubPage = null
            licenseDetailIndex = null
            subSlide.snapTo(0f)
        }
    }

    PredictiveBackHandler(enabled = hasSubLayer) { progressFlow ->
        try {
            progressFlow.collect { event -> subSlide.snapTo(event.progress.coerceIn(0f, 1f)) }
            subSlide.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
            themeSubPage = null
            licenseDetailIndex = null
            subSlide.snapTo(0f)
        } catch (cancellation: CancellationException) {
            subSlide.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium))
            throw cancellation
        }
    }

    PredictiveBackHandler(enabled = !hasSubLayer && overlayRoute != null) { progressFlow ->
        try {
            progressFlow.collect { event -> overlaySlide.snapTo(event.progress.coerceIn(0f, 1f)) }
            overlaySlide.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
            overlayRoute = null
            overlaySlide.snapTo(0f)
        } catch (cancellation: CancellationException) {
            overlaySlide.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium))
            throw cancellation
        }
    }

    BackHandler(enabled = overlayRoute == null && pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    if (controller.demoSpin) {
        LaunchedEffect(controller) {
            var lastFrameNanos = 0L
            while (true) {
                withFrameNanos { frameNanos ->
                    val dt = if (lastFrameNanos == 0L) {
                        0f
                    } else {
                        ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    }
                    lastFrameNanos = frameNanos
                    val next = normalizeDegrees(controller.targetAzimuth.floatValue + dt * 90f)
                    controller.targetAzimuth.floatValue = next
                    controller.displayedAzimuth.floatValue = next
                    controller.updateShownDegrees()
                }
            }
        }
    }

    AppScaffold(
        timeText = {
            val hideTime = controller.immersiveMode.value &&
                pagerState.currentPage == TAB_COMPASS &&
                overlayRoute == null
            if (!hideTime) TimeText()
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val isRound = LocalConfiguration.current.isScreenRound

            val topSlide = if (hasSubLayer) subSlide.value else overlaySlide.value

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val covered = if (overlayRoute != null) 1f - topSlide else 0f
                        val scale = 1f - 0.12f * covered
                        scaleX = scale
                        scaleY = scale
                        alpha = if (overlayRoute != null && covered > 0.999f) 0f else 1f
                    }
                    .drawWithContent {
                        drawContent()
                        if (overlayRoute != null) {
                            drawRect(Color.Black.copy(alpha = 0.5f * (1f - topSlide)))
                        }
                    },
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = overlayRoute == null,
                ) { page ->
                    when (page) {
                        TAB_COMPASS -> CompassScreen(
                            controller = controller,
                            isActive = overlayRoute == null && pagerState.currentPage == TAB_COMPASS,
                        )

                        else -> SettingsScreen(
                            controller = controller,
                            listState = settingsListState,
                            themePreferences = themePreferences,
                            isActive = overlayRoute == null && pagerState.currentPage == TAB_SETTINGS,
                            onOpenCalibration = { openOverlay(OverlayRoute.Calibration) },
                            onOpenThemeColor = { openOverlay(OverlayRoute.ThemeColor) },
                            onOpenAltitudeUnit = { openOverlay(OverlayRoute.AltitudeUnit) },
                        onOpenLicenses = { openOverlay(OverlayRoute.Licenses) },
                        onOpenMagneticBaseline = { openOverlay(OverlayRoute.MagneticBaseline) },
                            onToggleAltitudeCalibration = { enabled ->
                                if (enabled && !controller.hasFineLocationPermission()) {
                                    altitudePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                } else {
                                    controller.setAltitudeUseGpsCalibration(enabled)
                                }
                            },
                            onToggleTrueNorth = { enabled ->
                                if (enabled && !controller.hasLocationPermission()) {
                                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                } else {
                                    controller.setTrueNorth(enabled)
                                }
                            },
                        )
                    }
                }
            }

            if (!controller.immersiveMode.value &&
                overlayRoute == null &&
                abs(pagerState.currentPageOffsetFraction) > 0.01f
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(TAB_COUNT) { index ->
                        val selected = pagerState.currentPage == index
                        Box(
                            Modifier
                                .size(if (selected) 5.dp else 3.5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.White.copy(alpha = 0.35f)
                                    },
                                ),
                        )
                    }
                }
            }

            if (overlayRoute != null || overlaySlide.isRunning) {
                val isTop = !hasSubLayer
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxSize()

                        .background(MaterialTheme.colorScheme.background)
                        .then(
                            if (isTop) {

                                Modifier.wearDismissLayer(overlaySlide.value, widthPx, isRound)
                            } else {

                                Modifier.coveredLayer(1f - subSlide.value)
                            },
                        )
                        .then(
                            if (isTop) {
                                Modifier.clickable(interactionSource, indication = null) {}
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    when (overlayRoute) {
                        OverlayRoute.Calibration -> CalibrationScreen(
                            controller = controller,
                            isActive = true,
                            onBack = ::closeOverlay,
                        )

                        OverlayRoute.MagneticBaseline -> MagneticBaselineScreen(
                        controller = controller,
                        listState = baselineListState,
                        isActive = !hasSubLayer,
                        onBack = ::closeOverlay,
                    )

                    OverlayRoute.Licenses -> LicensesScreen(
                            listState = licensesListState,
                            isActive = !hasSubLayer,
                            onOpenLibrary = ::openLicenseDetail,
                            onBack = ::closeOverlay,
                        )

                    OverlayRoute.AltitudeUnit -> ChoicePage(
                            title = stringResource(R.string.altitude_unit),
                            choices = listOf(
                                AltitudeUnit.METERS.name to stringResource(R.string.altitude_unit_meters),
                                AltitudeUnit.FEET.name to stringResource(R.string.altitude_unit_feet),
                            ),
                            selected = controller.altitudeUnit.value.name,
                            onChoose = { controller.setAltitudeUnit(AltitudeUnit.valueOf(it)) },
                            listState = unitListState,
                            isActive = !hasSubLayer,
                            onBack = ::closeOverlay,
                        )

                        else -> ThemeColorMainPage(
                            themePreferences = themePreferences,
                            listState = themeListState,
                            isActive = !hasSubLayer,
                            onOpenSubPage = ::openSubPage,
                            onBack = ::closeOverlay,
                        )
                    }
                }
            }

            if (hasSubLayer || subSlide.isRunning) {
                val detailIndex = licenseDetailIndex
                val page = themeSubPage
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .wearDismissLayer(
                            progress = subSlide.value,
                            widthPx = widthPx,
                            isRound = isRound,
                        )
                        .clickable(interactionSource, indication = null) {},
                ) {
                    if (detailIndex != null) {
                        LicenseDetailScreen(
                            index = detailIndex,
                            listState = licenseDetailListState,
                            isActive = true,
                            onBack = ::closeSubLayer,
                        )
                    } else if (page != null) {
                        ThemeChoicePage(
                            subPage = page,
                            themePreferences = themePreferences,
                            listState = choiceListState,
                            isActive = true,
                            onBack = ::closeSubLayer,
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.coveredLayer(covered: Float): Modifier =
    this
        .graphicsLayer {
            val scale = 1f - 0.10f * covered
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (covered > 0f) {
                drawRect(Color.Black.copy(alpha = 0.5f * covered))
            }
        }

private fun Modifier.wearDismissLayer(progress: Float, widthPx: Float, isRound: Boolean): Modifier =
    this
        .graphicsLayer {
            val scale = lerp(1f, 0.7f, progress)
            val squeezeOffset = max(0f, (1f - scale) * widthPx / 2f)
            val slideFactor = ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f)
            scaleX = scale
            scaleY = scale
            translationX = lerp(squeezeOffset, widthPx, slideFactor)
        }
        .drawWithContent {
            drawContent()
            val scrim = (progress / 2f).coerceAtMost(0.3f)
            if (scrim > 0f) {
                val color = Color.Black.copy(alpha = scrim)
                if (isRound) {
                    drawCircle(color = color, radius = size.minDimension / 2f)
                } else {
                    drawRect(color = color)
                }
            }
        }
        .graphicsLayer {
            if (isRound && progress > 0.001f) {
                clip = true
                shape = CircleShape
            }
        }

private const val TAB_COUNT = 2
private const val TAB_COMPASS = 0
private const val TAB_SETTINGS = 1
