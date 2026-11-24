package com.example.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

object SiteLocationHelper {

    fun hasCoarseLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCoarseLocationSummary(context: Context): Result<String> {
        if (!hasCoarseLocationPermission(context)) {
            return Result.failure(SecurityException("لم يتم منح إذن الموقع التقريبي."))
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { continuation ->
            val cts = CancellationTokenSource()
            continuation.invokeOnCancellation { cts.cancel() }

            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val formatted = String.format(
                            Locale.US,
                            "موقع موثق (%.4f, %.4f)",
                            location.latitude,
                            location.longitude
                        )
                        continuation.resume(Result.success(formatted))
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { lastLoc ->
                                if (lastLoc != null) {
                                    val formatted = String.format(
                                        Locale.US,
                                        "آخر موقع موثق (%.4f, %.4f)",
                                        lastLoc.latitude,
                                        lastLoc.longitude
                                    )
                                    continuation.resume(Result.success(formatted))
                                } else {
                                    continuation.resume(
                                        Result.failure(
                                            IllegalStateException("تعذر تحديد الموقع الحالي. تأكد من تفعيل خدمة الموقع.")
                                        )
                                    )
                                }
                            }
                            .addOnFailureListener { err ->
                                continuation.resume(Result.failure(err))
                            }
                    }
                }
                .addOnFailureListener { error ->
                    continuation.resume(Result.failure(error))
                }
        }
    }
}
