package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed interface LocationState {
    data object Searching : LocationState
    data object PermissionRequired : LocationState
    data class Available(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val updatedAtMillis: Long
    ) : LocationState
    data class Unavailable(val reason: String) : LocationState
}

class LocationService(private val context: Context) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    fun observeLocationUpdates(hasPermission: Boolean): Flow<LocationState> = callbackFlow {
        if (!hasPermission) {
            trySend(LocationState.PermissionRequired)
            awaitClose {}
            return@callbackFlow
        }
        if (!isLocationEnabled()) {
            trySend(LocationState.Unavailable("Device GPS / Location services are turned off."))
            awaitClose {}
            return@callbackFlow
        }

        trySend(LocationState.Searching)

        // Seed with last known location immediately if present
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                trySend(
                    LocationState.Available(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMeters = loc.accuracy,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMinUpdateDistanceMeters(2f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc != null) {
                    trySend(
                        LocationState.Available(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracyMeters = loc.accuracy,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            trySend(LocationState.PermissionRequired)
        } catch (e: Exception) {
            trySend(LocationState.Unavailable(e.localizedMessage ?: "Unable to access GPS"))
        }

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getFreshLocation(hasPermission: Boolean): LocationState {
        if (!hasPermission) return LocationState.PermissionRequired
        if (!isLocationEnabled()) {
            return LocationState.Unavailable("Device GPS / Location services are turned off.")
        }
        return suspendCancellableCoroutine { cont ->
            try {
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            cont.resume(
                                LocationState.Available(
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    accuracyMeters = loc.accuracy,
                                    updatedAtMillis = System.currentTimeMillis()
                                )
                            )
                        } else {
                            fusedClient.lastLocation
                                .addOnSuccessListener { last ->
                                    if (last != null) {
                                        cont.resume(
                                            LocationState.Available(
                                                latitude = last.latitude,
                                                longitude = last.longitude,
                                                accuracyMeters = last.accuracy,
                                                updatedAtMillis = System.currentTimeMillis()
                                            )
                                        )
                                    } else {
                                        cont.resume(LocationState.Unavailable("GPS fix unavailable. Move to an open area and retry."))
                                    }
                                }
                                .addOnFailureListener { err ->
                                    cont.resume(LocationState.Unavailable(err.localizedMessage ?: "GPS unavailable"))
                                }
                        }
                    }
                    .addOnFailureListener { err ->
                        cont.resume(LocationState.Unavailable(err.localizedMessage ?: "GPS unavailable"))
                    }
            } catch (e: SecurityException) {
                cont.resume(LocationState.PermissionRequired)
            }
        }
    }

    companion object {
        fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lng1, lat2, lng2, results)
            return results[0].toDouble()
        }

        fun formatDistance(meters: Double): String {
            return if (meters < 1000) {
                "${meters.toInt()} m"
            } else {
                String.format("%.2f km", meters / 1000.0)
            }
        }
    }
}
