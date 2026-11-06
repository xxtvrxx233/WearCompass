package io.github.xxtvrxx233.wearcompass.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class HeadingSample(
    val azimuthDegrees: Float,
    val tiltDegrees: Float,
    val headingAccuracyRadians: Float,
    val magnetometerAccuracy: Int,

    val fieldMicroTesla: Float,

    val pressureHpa: Float,

    val calibrationProgress: Float = 0f,

    val calibrationReady: Boolean = false,

    val calibrationDrift: Boolean = false,

    val calibrationNeedsFlip: Boolean = false,
)

class CompassEngine(context: Context) : SensorEventListener {

    fun interface Listener {
        fun onHeading(sample: HeadingSample)
    }

    private val sensorManager: SensorManager =
        context.applicationContext.getSystemService(SensorManager::class.java)
    private val windowManager: WindowManager =
        context.applicationContext.getSystemService(WindowManager::class.java)

    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val geomagneticRotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val pressureSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val magnetometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val magCalibration = MagCalibrationStore(context)
    private val calibrationSampler = MagCalibrationSampler()
    private var calibrationActive = false
    private var calibrationDrift = false

    private val displayManager: DisplayManager? =
        context.applicationContext.getSystemService(DisplayManager::class.java)

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            val rotation = currentDisplayRotation()
            if (rotation == displayRotation) return
            displayRotation = rotation
            azimuth = Float.NaN
        }
    }

    private val rotationMatrix = FloatArray(9)
    private val screenMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val magValues = FloatArray(3)

    private var listener: Listener? = null
    private var fusedSensor: Sensor? = null
    private var splitMode = false
    private var haveGravity = false
    private var haveGeomagnetic = false

    private var fieldMicroTesla = Float.NaN
    private var pressureHpa = Float.NaN
    private var azimuth = Float.NaN
    private var headingAccuracy = -1f
    private var accuracyStatus = UNKNOWN_ACCURACY
    @Volatile
    private var displayRotation = Surface.ROTATION_0
    private var lastEmitNanos = 0L
    private var lastLogNanos = 0L

    val hasDirectionSensor: Boolean
        get() = rotationVectorSensor != null ||
            geomagneticRotationSensor != null ||
            (accelerometer != null && magnetometer != null)

    fun start(listener: Listener) {
        if (this.listener != null) return
        this.listener = listener
        azimuth = Float.NaN
        headingAccuracy = -1f
        accuracyStatus = UNKNOWN_ACCURACY
        haveGravity = false
        haveGeomagnetic = false
        displayRotation = currentDisplayRotation()
        displayManager?.registerDisplayListener(displayListener, null)

        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        fusedSensor = rotationVectorSensor ?: geomagneticRotationSensor
        splitMode = fusedSensor == null
        fusedSensor?.let { sensorManager.registerListener(this, it, SENSOR_PERIOD_US) }
        magnetometer?.let { sensorManager.registerListener(this, it, SENSOR_PERIOD_US) }
        accelerometer?.let { sensorManager.registerListener(this, it, SENSOR_PERIOD_US) }
    }

    fun stop() {
        listener = null
        fieldMicroTesla = Float.NaN
        pressureHpa = Float.NaN
        sensorManager.unregisterListener(this)
        displayManager?.unregisterDisplayListener(displayListener)
        fusedSensor = null
        splitMode = false
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD ||
            sensor.type == Sensor.TYPE_ROTATION_VECTOR ||
            sensor.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
        ) {
            accuracyStatus = accuracy
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                if (magCalibration.hasCalibration) return
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                headingAccuracy = if (event.values.size >= 5) event.values[4] else -1f
            }

            Sensor.TYPE_PRESSURE -> {
                val pressure = event.values.firstOrNull() ?: return
                if (pressure > 0f) {
                    pressureHpa = if (pressureHpa.isNaN()) pressure else pressureHpa * 0.8f + pressure * 0.2f
                }
            }

            Sensor.TYPE_ACCELEROMETER -> {
                lowPass(event.values, gravity, if (haveGravity) 0.25f else 0f)
                haveGravity = true
                if (!magCalibration.hasCalibration && !splitMode) return
                if (!haveGeomagnetic) return
                if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) return
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                for (i in 0 until minOf(3, event.values.size)) magValues[i] = event.values[i]

                val corrected = magCalibration.apply(
                    event.values[0], event.values[1], event.values[2],
                )
                updateFieldStrength(corrected)
                updateCalibrationHealth(corrected)
                if (calibrationActive) {
                    calibrationSampler.add(event.values[0], event.values[1], event.values[2])
                }

                if (!magCalibration.hasCalibration && !splitMode) return
                lowPass(corrected, geomagnetic, if (haveGeomagnetic) 0.25f else 0f)
                haveGeomagnetic = true
                if (!haveGravity) return
                if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) return
            }

            else -> return
        }
        emit()
    }

    private fun emit() {
        val now = System.nanoTime()
        if (now - lastEmitNanos < EMIT_INTERVAL_NANOS) return
        lastEmitNanos = now

        remapToScreen()
        SensorManager.getOrientation(screenMatrix, orientation)
        val rawAzimuth = normalizeDegrees(Math.toDegrees(orientation[0].toDouble()).toFloat())
        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
        val tilt = sqrt(pitch * pitch + roll * roll)

        azimuth = smoothAzimuth(azimuth, rawAzimuth)
        if (Log.isLoggable(TAG, Log.DEBUG) && now - lastLogNanos > LOG_INTERVAL_NANOS) {
            lastLogNanos = now
            val sensor = fusedSensor
            Log.d(
                TAG,
                "azimuth=%.2f° raw=%.2f° tilt=%.1f° field=%.1fµT p=%.1fhPa "
                    .format(azimuth, rawAzimuth, tilt, fieldMicroTesla, pressureHpa) +
                    "rot=$displayRotation status=$accuracyStatus " +
                    "mag=(%.1f,%.1f,%.1f) ".format(magValues[0], magValues[1], magValues[2]) +
                    "sensor=${sensor?.name ?: "accel+mag"} split=$splitMode",
            )
        }
        listener?.onHeading(
            HeadingSample(
                azimuthDegrees = azimuth,
                tiltDegrees = tilt,
                headingAccuracyRadians = headingAccuracy,
                magnetometerAccuracy = accuracyStatus,
                fieldMicroTesla = fieldMicroTesla,
                pressureHpa = pressureHpa,
                calibrationProgress = if (calibrationActive) calibrationSampler.progress else 0f,
                calibrationReady = calibrationActive && calibrationSampler.ready,
                calibrationDrift = calibrationDrift,
                calibrationNeedsFlip = calibrationActive && calibrationSampler.needsFlip,
            ),
        )
    }

    private fun remapToScreen() {
        when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, screenMatrix,
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, screenMatrix,
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, screenMatrix,
            )
            else -> rotationMatrix.copyInto(screenMatrix)
        }
    }

    private fun currentDisplayRotation(): Int =
        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

    private fun updateCalibrationHealth(corrected: FloatArray) {
        if (!magCalibration.hasCalibration) {
            calibrationDrift = false
            return
        }
        val expected = magCalibration.expectedStrength
        if (expected <= 0f) return
        val strength = sqrt(
            corrected[0] * corrected[0] + corrected[1] * corrected[1] + corrected[2] * corrected[2],
        )
        if (strength <= 0f) return
        calibrationDrift = abs(strength - expected) / expected > CALIBRATION_DRIFT_RATIO
    }

    fun beginCalibration() {
        calibrationSampler.reset()
        calibrationActive = true
        calibrationDrift = false
    }

    fun finishCalibration(): Boolean {
        if (!calibrationActive) return false
        val result = calibrationSampler.fit()
        calibrationActive = false
        if (result == null) return false
        magCalibration.save(result)
        azimuth = Float.NaN
        haveGeomagnetic = false
        return true
    }

    fun cancelCalibration() {
        calibrationActive = false
        calibrationSampler.reset()
    }

    fun clearCalibration() {
        magCalibration.clear()
        calibrationActive = false
        calibrationDrift = false
        azimuth = Float.NaN
        haveGeomagnetic = false
    }

    val hasCalibration: Boolean
        get() = magCalibration.hasCalibration

    val calibratedStrength: Float
        get() = magCalibration.expectedStrength

    private fun updateFieldStrength(values: FloatArray) {
        if (values.size < 3) return
        val magnitude = sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2],
        )
        fieldMicroTesla = if (fieldMicroTesla.isNaN()) magnitude else fieldMicroTesla * 0.8f + magnitude * 0.2f
    }

    private fun lowPass(input: FloatArray, output: FloatArray, alpha: Float) {
        for (i in 0 until minOf(input.size, output.size)) {
            output[i] = if (alpha <= 0f) input[i] else output[i] + alpha * (input[i] - output[i])
        }
    }

    private fun smoothAzimuth(previous: Float, next: Float): Float {
        if (previous.isNaN()) return next
        var delta = next - previous
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f

        val magnitude = abs(delta)
        val alpha = when {
            magnitude > 12f -> 0.85f
            magnitude > 3f -> 0.45f
            else -> 0.08f
        }
        return normalizeDegrees(previous + delta * alpha)
    }

    companion object {
        private const val TAG = "CompassEngine"
        private const val EMIT_INTERVAL_NANOS = 8_000_000L

        private const val SENSOR_PERIOD_US = 16_000
        private const val LOG_INTERVAL_NANOS = 2_000_000_000L
        private const val CALIBRATION_DRIFT_RATIO = 0.35f
    }
}

const val UNKNOWN_ACCURACY: Int = -1

const val HEADING_ACCURACY_PLACEHOLDER: Float = 1.5707964f

fun normalizeDegrees(value: Float): Float {
    var result = value % 360f
    if (result < 0f) result += 360f
    return result
}
