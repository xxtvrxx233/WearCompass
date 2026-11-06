package io.github.xxtvrxx233.wearcompass.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

class DeclinationSource(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager: LocationManager =
        appContext.getSystemService(LocationManager::class.java)
    private var lastRequestAtMillis = 0L

    var locationAltitudeMeters: Float? = null
        private set

    fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun requestDeclination(onResult: (Float) -> Unit, onUnavailable: () -> Unit) {
        if (!hasPermission()) {
            onUnavailable()
            return
        }
        val cached = bestLastKnownLocation()
        if (cached != null) {
            onResult(declinationOf(cached))
            if (System.currentTimeMillis() - cached.time < FRESH_LOCATION_MILLIS) return
        }
        val now = System.currentTimeMillis()
        if (now - lastRequestAtMillis < MIN_REQUEST_INTERVAL_MILLIS) {
            if (cached == null) onUnavailable()
            return
        }
        lastRequestAtMillis = now
        requestSingleFix(onResult, onUnavailable)
    }

    private fun requestSingleFix(onResult: (Float) -> Unit, onUnavailable: () -> Unit) {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        val provider = candidates.firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        } ?: run {
            onUnavailable()
            return
        }
        val requested = runCatching {
            locationManager.getCurrentLocation(provider, null, appContext.mainExecutor) { location ->
                if (location != null) onResult(declinationOf(location)) else onUnavailable()
            }
        }
        if (requested.isFailure) onUnavailable()
    }

    private fun bestLastKnownLocation(): Location? {
        var best: Location? = null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        for (provider in providers) {
            val location = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || location.time > best.time) best = location
        }
        return best
    }

    private fun declinationOf(location: Location): Float {
        if (location.hasAltitude()) locationAltitudeMeters = location.altitude.toFloat()
        return GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis(),
        ).declination
    }

    private companion object {
        const val FRESH_LOCATION_MILLIS = 30 * 60 * 1000L
        const val MIN_REQUEST_INTERVAL_MILLIS = 60 * 1000L
    }
}
