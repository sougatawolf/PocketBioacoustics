package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

enum class GpsTagMode(
    val label: String,
    val buttonBadge: String,
    val description: String
) {
    OFF(
        label = "Off",
        buttonBadge = "Off",
        description = "No GPS tagging (saves battery)"
    ),
    ONE_TIME(
        label = "One-Time GPS",
        buttonBadge = "1-Time",
        description = "Capture 1 fix when Record is pressed"
    ),
    EACH_FILE(
        label = "GPS Each File",
        buttonBadge = "Each File",
        description = "Capture 1 fix when each file starts"
    )
}

data class GpsCoordinate(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val capturedAtMs: Long = System.currentTimeMillis()
) {
    fun formatShort(): String {
        val latDir = if (latitude >= 0.0) "N" else "S"
        val lonDir = if (longitude >= 0.0) "E" else "W"
        return String.format(
            Locale.US,
            "%.4f° %s, %.4f° %s",
            abs(latitude),
            latDir,
            abs(longitude),
            lonDir
        )
    }

    fun formatDetailed(): String {
        val acc = accuracyMeters?.let { " (±${it.toInt()}m)" }.orEmpty()
        return "${formatShort()}$acc"
    }
}

class GpsTagManager(private val context: Context) {

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Acquires at most ONE location fix and immediately releases the GPS radio
     * so battery drain stays minimal between scheduled intervals.
     */
    @SuppressLint("MissingPermission")
    suspend fun acquireSingleFix(timeoutMs: Long = 7000L): GpsCoordinate? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val provider = selectBestProvider(manager)
        val fresh = if (provider != null) {
            withTimeoutOrNull(timeoutMs) {
                requestOneShotLocation(manager, provider)
            }
        } else {
            null
        }

        val resolved = fresh ?: getBestLastKnownLocation(manager)
        return resolved?.let { loc ->
            GpsCoordinate(
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                capturedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun selectBestProvider(manager: LocationManager): String? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        for (p in candidates) {
            try {
                if (manager.isProviderEnabled(p)) {
                    return p
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOneShotLocation(
        manager: LocationManager,
        provider: String
    ): Location? = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellationSignal = CancellationSignal()
                cont.invokeOnCancellation {
                    cancellationSignal.cancel()
                }
                try {
                    manager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        context.mainExecutor
                    ) { location ->
                        if (cont.isActive) {
                            cont.resume(location)
                        }
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try {
                            manager.removeUpdates(this)
                        } catch (_: Exception) {
                        }
                        if (cont.isActive) {
                            cont.resume(location)
                        }
                    }

                    @Deprecated("Deprecated in Android API 29")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                cont.invokeOnCancellation {
                    try {
                        manager.removeUpdates(listener)
                    } catch (_: Exception) {
                    }
                }
                try {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(manager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            val loc = try {
                manager.getLastKnownLocation(p)
            } catch (_: Exception) {
                null
            } ?: continue
            if (best == null || loc.accuracy < best.accuracy) {
                best = loc
            }
        }
        return best
    }
}
