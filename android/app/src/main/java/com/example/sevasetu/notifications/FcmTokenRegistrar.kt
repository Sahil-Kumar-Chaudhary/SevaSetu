package com.example.sevasetu.notifications

import android.content.Context
import android.util.Log
import com.example.sevasetu.data.remote.api.RegisterDeviceTokenRequest
import com.example.sevasetu.network.ApiService
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FcmTokenRegistrar {
    private const val TAG = "FcmTokenRegistrar"

    /**
     * Initialize notification channels but skip backend registration.
     * Call this early in app lifecycle (in SplashScreen).
     * backend registration will happen after user authenticates.
     */
    fun initializeNotificationChannels(context: Context) {
        Log.d(TAG, "Initializing notification channels...")
        NotificationSupport.createChannels(context)
    }

    /**
     * Register the current FCM token with the backend.
     * Call this AFTER user authentication succeeds (in Dashboard or Login).
     * Only works when user is authenticated.
     */
    fun registerCurrentToken(context: Context) {
        Log.d(TAG, "Fetching and registering current FCM token (requires authentication)...")
        NotificationSupport.createChannels(context)

        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    Log.d(TAG, "FCM token retrieved: ${token.take(10)}...")
                    registerToken(context, token)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to fetch FCM token", exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FirebaseMessaging instance", e)
        }
    }

    fun registerToken(context: Context, token: String) {
        if (token.isBlank()) {
            Log.w(TAG, "Token is blank, skipping registration")
            return
        }

        Log.d(TAG, "Registering device token with backend...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiService.userApi(context).registerDeviceToken(
                    RegisterDeviceTokenRequest(token = token)
                )
                if (response.isSuccessful) {
                    Log.d(TAG, "✓ Device token registered successfully")
                } else {
                    when (response.code()) {
                        401, 403 -> {
                            Log.w(TAG, "registerDeviceToken failed: HTTP ${response.code()} - User not authenticated. Token will be registered after login.")
                        }
                        404 -> {
                            Log.e(TAG, "registerDeviceToken failed: HTTP 404 - Endpoint not found on backend. Check /users/me/device-tokens route.")
                        }
                        else -> {
                            Log.e(TAG, "registerDeviceToken failed: HTTP ${response.code()}")
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "registerDeviceToken network error", error)
            }
        }
    }
}
