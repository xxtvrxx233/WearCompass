package io.github.xxtvrxx233.wearcompass.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.sqrt

class MagneticFieldMonitor(context: Context) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.applicationContext.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val magnitudeMicroTesla = mutableFloatStateOf(Float.NaN)

    fun start() {
        magnitudeMicroTesla.floatValue = Float.NaN
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        if (values.size < 3) return
        val magnitude = sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2],
        )
        val previous = magnitudeMicroTesla.floatValue
        magnitudeMicroTesla.floatValue = if (previous.isNaN()) magnitude else previous * 0.8f + magnitude * 0.2f
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
}
