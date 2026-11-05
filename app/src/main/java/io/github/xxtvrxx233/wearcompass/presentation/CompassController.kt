package io.github.xxtvrxx233.wearcompass.presentation

import android.content.Context
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import io.github.xxtvrxx233.wearcompass.data.CompassEngine
import io.github.xxtvrxx233.wearcompass.data.HEADING_ACCURACY_PLACEHOLDER
import io.github.xxtvrxx233.wearcompass.data.DeclinationSource
import io.github.xxtvrxx233.wearcompass.data.HeadingSample
import io.github.xxtvrxx233.wearcompass.data.normalizeDegrees
import kotlin.math.abs
import kotlin.math.pow

enum class CompassHint {
    NONE,
    LOCATING,
    NO_LOCATION_PERMISSION,
    NO_SENSOR,
}

enum class AltitudeUnit {
    METERS,
    FEET,
}

class CompassController(
    private val context: Context,

    val demoHeading: Float?,

    val demoSpin: Boolean = false,
) : CompassEngine.Listener {

    private val engine = CompassEngine(context)
    private val declinationSource = DeclinationSource(context)
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    val sensorAvailable = mutableStateOf(engine.hasDirectionSensor)
    val targetAzimuth = mutableFloatStateOf(0f)
    val displayedAzimuth = mutableFloatStateOf(0f)
    val tiltDegrees = mutableFloatStateOf(0f)

    val altitudeMeters = mutableFloatStateOf(Float.NaN)

    val fieldMicroTesla = mutableFloatStateOf(Float.NaN)

    val baselineMicroTesla = mutableFloatStateOf(
        preferences.getFloat(KEY_BASELINE, Float.NaN).takeIf { it > 0f } ?: Float.NaN,
    )

    val thresholdMultiplier = mutableFloatStateOf(preferences.getFloat(KEY_MULTIPLIER, 2f))

    val isSamplingBaseline = mutableStateOf(false)

    val baselineSamplingProgress = mutableFloatStateOf(0f)

    val magneticInterference = mutableStateOf(false)

    val isCharging = mutableStateOf(false)

    val chargingInterference = mutableStateOf(false)

    val shownDegrees = mutableIntStateOf(0)
    val needsCalibration = mutableStateOf(false)
    val useTrueNorth = mutableStateOf(preferences.getBoolean(KEY_TRUE_NORTH, false))
    val declinationDegrees = mutableFloatStateOf(Float.NaN)

    val levelIndicatorEnabled = mutableStateOf(preferences.getBoolean(KEY_LEVEL_INDICATOR, true))

    val altitudeUnit = mutableStateOf(
        runCatching {
            AltitudeUnit.valueOf(preferences.getString(KEY_ALTITUDE_UNIT, AltitudeUnit.METERS.name)!!)
        }.getOrDefault(AltitudeUnit.METERS),
    )

    val altitudeUseGpsCalibration = mutableStateOf(preferences.getBoolean(KEY_ALTITUDE_GPS, false))

    private var lastPressureHpa = Float.NaN
    val hint = mutableStateOf(CompassHint.NONE)

    private var lastMagneticAzimuth = 0f
    private var hasHeading = false
    private val clearHint = Runnable { hint.value = CompassHint.NONE }

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(receiverContext: android.content.Context?, intent: android.content.Intent?) {
            val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            isCharging.value = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
            refreshChargingInterference()
        }
    }

    fun start() {
        sensorAvailable.value = engine.hasDirectionSensor
        demoHeading?.let { demo ->
            hasHeading = true
            targetAzimuth.floatValue = normalizeDegrees(demo)
            displayedAzimuth.floatValue = normalizeDegrees(demo)
            shownDegrees.intValue = ((normalizeDegrees(demo) + 0.5f).toInt()) % 360
        }
        if (demoHeading == null && engine.hasDirectionSensor) engine.start(this)
        context.registerReceiver(
            batteryReceiver,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
        )
        if (useTrueNorth.value) refreshDeclination()

        if (altitudeUseGpsCalibration.value) refreshLocationForAltitude()
    }

    fun stop() {
        runCatching { context.unregisterReceiver(batteryReceiver) }
        engine.stop()
        hasHeading = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onHeading(sample: HeadingSample) {
        lastMagneticAzimuth = sample.azimuthDegrees
        val target = applyNorthReference(sample.azimuthDegrees)

        var targetDelta = target - targetAzimuth.floatValue
        if (targetDelta > 180f) targetDelta -= 360f
        if (targetDelta < -180f) targetDelta += 360f
        if (!hasHeading || abs(targetDelta) > TARGET_STATE_STEP_DEGREES) {
            targetAzimuth.floatValue = target
        }
        if (!hasHeading) {
            hasHeading = true
            displayedAzimuth.floatValue = target

            updateShownDegrees()
        }

        if (abs(sample.tiltDegrees - tiltDegrees.floatValue) > TILT_STATE_STEP_DEGREES) {
            tiltDegrees.floatValue = sample.tiltDegrees
        }
        updateMagneticField(sample.fieldMicroTesla)
        updatePressure(sample.pressureHpa)
        needsCalibration.value = shouldSuggestCalibration(sample)
    }

    private fun updateMagneticField(field: Float) {
        if (field.isNaN()) return
        fieldMicroTesla.floatValue = field
        refreshChargingInterference()
        val enterMax = interferenceEnterMax()
        val exitMax = interferenceExitMax()
        magneticInterference.value = if (magneticInterference.value) {

            field < INTERFERENCE_EXIT_MIN || field > exitMax
        } else {

            field > enterMax || field < INTERFERENCE_ENTER_MIN
        }
    }

    private fun updatePressure(pressure: Float) {
        if (pressure.isNaN() || pressure <= 0f) return
        lastPressureHpa = pressure
        applyAltitude(computeAltitude(pressure))
    }

    private fun refreshAltitude() {
        if (lastPressureHpa.isNaN()) return
        applyAltitude(computeAltitude(lastPressureHpa))
    }

    private fun applyAltitude(altitude: Float) {
        if (altitude.isNaN()) return
        val current = altitudeMeters.floatValue
        if (current.isNaN() || abs(altitude - current) > ALTITUDE_STATE_STEP_METERS) {
            altitudeMeters.floatValue = altitude
        }
    }

    private fun computeAltitude(pressure: Float): Float {
        val fixedBase = 44300f * (1f - (pressure / SEA_LEVEL_PRESSURE_HPA).pow(ALTITUDE_EXPONENT))
        if (!altitudeUseGpsCalibration.value) return fixedBase
        val knownAltitude = declinationSource.locationAltitudeMeters ?: return fixedBase
        val ratio = 1f - knownAltitude / ALTITUDE_COEFFICIENT
        if (ratio <= 0.01f) return fixedBase

        val localSeaLevel = pressure / ratio.pow(1f / ALTITUDE_EXPONENT)
        return ALTITUDE_COEFFICIENT * (1f - (pressure / localSeaLevel).pow(ALTITUDE_EXPONENT))
    }

    fun interferenceEnterMax(): Float {
        val baseline = baselineMicroTesla.floatValue
        return if (baseline.isNaN()) INTERFERENCE_ENTER_MAX else baseline * thresholdMultiplier.floatValue
    }

    fun interferenceExitMax(): Float {
        val baseline = baselineMicroTesla.floatValue
        return if (baseline.isNaN()) INTERFERENCE_EXIT_MAX else interferenceEnterMax() * 0.8f
    }

    fun setThresholdMultiplier(multiplier: Float) {
        thresholdMultiplier.floatValue = multiplier
        preferences.edit().putFloat(KEY_MULTIPLIER, multiplier).apply()
        refreshInterferenceNow()
    }

    fun clearBaseline() {
        baselineMicroTesla.floatValue = Float.NaN
        preferences.edit().remove(KEY_BASELINE).apply()
        refreshInterferenceNow()
    }

    private fun refreshInterferenceNow() {
        val field = fieldMicroTesla.floatValue
        if (field.isNaN()) return
        magneticInterference.value = field > interferenceEnterMax() || field < INTERFERENCE_ENTER_MIN
    }

    fun startBaselineSampling() {
        if (isSamplingBaseline.value) return
        if (isCharging.value) {
            showHint(CompassHint.NO_LOCATION_PERMISSION)
            return
        }
        isSamplingBaseline.value = true
        baselineSamplingProgress.floatValue = 0f
        val samples = ArrayList<Float>(BASELINE_SAMPLE_COUNT)

        fun step(index: Int) {
            if (!isSamplingBaseline.value) return
            if (isCharging.value) {

                isSamplingBaseline.value = false
                baselineSamplingProgress.floatValue = 0f
                return
            }
            if (index >= BASELINE_SAMPLE_COUNT) {
                isSamplingBaseline.value = false
                baselineSamplingProgress.floatValue = 0f
                if (samples.size >= BASELINE_SAMPLE_COUNT / 2) {
                    val sorted = samples.sorted()
                    val median = if (sorted.size % 2 == 1) {
                        sorted[sorted.size / 2]
                    } else {
                        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
                    }
                    baselineMicroTesla.floatValue = median
                    preferences.edit().putFloat(KEY_BASELINE, median).apply()
                    refreshInterferenceNow()
                }
                return
            }
            val field = fieldMicroTesla.floatValue
            if (!field.isNaN()) samples.add(field)
            baselineSamplingProgress.floatValue = index / BASELINE_SAMPLE_COUNT.toFloat()
            handler.postDelayed({ step(index + 1) }, BASELINE_SAMPLE_INTERVAL_MILLIS)
        }

        handler.postDelayed({ step(0) }, BASELINE_SAMPLE_INTERVAL_MILLIS)
    }

    private fun refreshChargingInterference() {
        chargingInterference.value = isCharging.value &&
            !fieldMicroTesla.floatValue.isNaN() &&
            fieldMicroTesla.floatValue > CHARGER_FIELD_THRESHOLD
    }

    fun currentHeading(): Float = displayedAzimuth.floatValue

    fun updateShownDegrees() {
        val value = displayedAzimuth.floatValue
        val current = shownDegrees.intValue
        var offset = value - (current + 0.5f)
        if (offset > 180f) offset -= 360f
        if (offset < -180f) offset += 360f
        when {
            abs(offset) > 1.5f -> shownDegrees.intValue = ((value + 0.5f).toInt()) % 360
            offset > 0.5f + SHOWN_HYSTERESIS -> shownDegrees.intValue = (current + 1) % 360
            offset < -(0.5f + SHOWN_HYSTERESIS) -> shownDegrees.intValue = (current + 359) % 360
        }
    }

    fun setAltitudeUnit(unit: AltitudeUnit) {
        altitudeUnit.value = unit
        preferences.edit().putString(KEY_ALTITUDE_UNIT, unit.name).apply()
    }

    fun setAltitudeUseGpsCalibration(enabled: Boolean) {
        altitudeUseGpsCalibration.value = enabled
        preferences.edit().putBoolean(KEY_ALTITUDE_GPS, enabled).apply()
        if (enabled) refreshLocationForAltitude() else refreshAltitude()
    }

    private fun refreshLocationForAltitude() {
        if (!declinationSource.hasFinePermission()) {
            showHint(CompassHint.NO_LOCATION_PERMISSION)
            return
        }
        showHint(CompassHint.LOCATING, 8000)
        declinationSource.requestDeclination(
            onResult = { declination ->
                handler.post {
                    declinationDegrees.floatValue = declination
                    refreshAltitude()
                    if (hint.value == CompassHint.LOCATING) hint.value = CompassHint.NONE
                }
            },
            onUnavailable = { handler.post { refreshAltitude() } },
        )
    }

    fun onAltitudePermissionResult(granted: Boolean) {
        if (granted) {
            setAltitudeUseGpsCalibration(true)
        } else {
            altitudeUseGpsCalibration.value = false
            preferences.edit().putBoolean(KEY_ALTITUDE_GPS, false).apply()
            showHint(CompassHint.NO_LOCATION_PERMISSION)
        }
    }

    fun setLevelIndicatorEnabled(enabled: Boolean) {
        levelIndicatorEnabled.value = enabled
        preferences.edit().putBoolean(KEY_LEVEL_INDICATOR, enabled).apply()
    }

    fun hasLocationPermission(): Boolean = declinationSource.hasPermission()

    fun hasFineLocationPermission(): Boolean = declinationSource.hasFinePermission()

    fun setTrueNorth(enabled: Boolean) {
        if (useTrueNorth.value == enabled) return
        useTrueNorth.value = enabled
        preferences.edit().putBoolean(KEY_TRUE_NORTH, enabled).apply()
        targetAzimuth.floatValue = applyNorthReference(lastMagneticAzimuth)
        if (enabled) refreshDeclination()
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            useTrueNorth.value = true
            preferences.edit().putBoolean(KEY_TRUE_NORTH, true).apply()
            refreshDeclination()
        } else {
            showHint(CompassHint.NO_LOCATION_PERMISSION)
        }
    }

    fun refreshDeclination() {
        if (!declinationSource.hasPermission()) {
            showHint(CompassHint.NO_LOCATION_PERMISSION)
            return
        }
        if (declinationDegrees.floatValue.isNaN()) showHint(CompassHint.LOCATING)
        declinationSource.requestDeclination(
            onResult = { declination ->
                handler.post {
                    declinationDegrees.floatValue = declination
                    targetAzimuth.floatValue = applyNorthReference(lastMagneticAzimuth)
                    if (hint.value == CompassHint.LOCATING) hint.value = CompassHint.NONE
                }
            },
            onUnavailable = {
                handler.post {
                    if (hint.value == CompassHint.LOCATING) showHint(CompassHint.LOCATING, 6000)
                }
            },
        )
    }

    private fun shouldSuggestCalibration(sample: HeadingSample): Boolean {
        val status = sample.magnetometerAccuracy
        if (status == SensorManager.SENSOR_STATUS_UNRELIABLE) return true
        if (status == SensorManager.SENSOR_STATUS_ACCURACY_HIGH) return false
        val accuracy = sample.headingAccuracyRadians
        val provided = accuracy >= 0f && abs(accuracy - HEADING_ACCURACY_PLACEHOLDER) > 0.02f
        return provided && accuracy > CALIBRATION_WARN_RADIANS
    }

    private fun applyNorthReference(magnetic: Float): Float {
        val declination = declinationDegrees.floatValue
        return if (useTrueNorth.value && !declination.isNaN()) {
            normalizeDegrees(magnetic + declination)
        } else {
            magnetic
        }
    }

    private fun showHint(value: CompassHint, durationMillis: Long = DEFAULT_HINT_MILLIS) {
        handler.removeCallbacks(clearHint)
        hint.value = value
        handler.postDelayed(clearHint, durationMillis)
    }

    private companion object {
        const val PREFS_NAME = "wear_compass"
        const val KEY_TRUE_NORTH = "true_north"
        const val KEY_LEVEL_INDICATOR = "level_indicator"
        const val KEY_ALTITUDE_GPS = "altitude_gps"
        const val KEY_ALTITUDE_UNIT = "altitude_unit"
        const val CALIBRATION_WARN_RADIANS = 0.5f
        const val TILT_STATE_STEP_DEGREES = 0.3f
        const val TARGET_STATE_STEP_DEGREES = 0.05f

        const val ALTITUDE_STATE_STEP_METERS = 0.5f

        const val CHARGER_FIELD_THRESHOLD = 2000f
        const val KEY_BASELINE = "magnetic_baseline"
        const val KEY_MULTIPLIER = "baseline_multiplier"
        const val BASELINE_SAMPLE_COUNT = 25
        const val BASELINE_SAMPLE_INTERVAL_MILLIS = 200L
        const val ALTITUDE_COEFFICIENT = 44300f
        const val ALTITUDE_EXPONENT = 0.19026f
        const val SEA_LEVEL_PRESSURE_HPA = 1013.25f
        const val INTERFERENCE_ENTER_MIN = 10f
        const val INTERFERENCE_ENTER_MAX = 300f
        const val INTERFERENCE_EXIT_MIN = 20f
        const val INTERFERENCE_EXIT_MAX = 220f
        const val SHOWN_HYSTERESIS = 0.25f
        const val DEFAULT_HINT_MILLIS = 4000L
    }
}
