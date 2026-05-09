package com.example.sevasetu.data.repository

import com.example.sevasetu.data.remote.api.UserApi
import com.example.sevasetu.data.remote.dto.UpdateProfileRequest
import com.example.sevasetu.data.remote.dto.UserActivityResponse
import com.example.sevasetu.data.remote.dto.UserProfileDto
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException

class UserRepository(
    private val userApi: UserApi
) {
    suspend fun getMe(): Result<UserProfileDto> = runCatching {
        val response = userApi.getMe()
        if (!response.isSuccessful) {
            throw IllegalStateException(extractError(response.code(), response.errorBody()?.string(), "load profile"))
        }
        response.body()?.user ?: throw IllegalStateException("Empty profile response")
    }.recoverCatching { error ->
        throw IllegalStateException(mapNetworkError(error, "load profile"))
    }

    suspend fun updateMe(request: UpdateProfileRequest): Result<UserProfileDto> = runCatching {
        val response = userApi.updateMe(request)
        if (!response.isSuccessful) {
            throw IllegalStateException(extractError(response.code(), response.errorBody()?.string(), "update profile"))
        }
        response.body()?.user ?: throw IllegalStateException("Empty profile response")
    }.recoverCatching { error ->
        throw IllegalStateException(mapNetworkError(error, "update profile"))
    }

    suspend fun getMyActivity(page: Int = 1, limit: Int = 20): Result<UserActivityResponse> = runCatching {
        val response = userApi.getMyActivity(page = page, limit = limit)
        if (!response.isSuccessful) {
            throw IllegalStateException(extractError(response.code(), response.errorBody()?.string(), "load activity"))
        }
        response.body() ?: throw IllegalStateException("Empty activity response")
    }.recoverCatching { error ->
        throw IllegalStateException(mapNetworkError(error, "load activity"))
    }

    suspend fun removeDeviceToken(token: String): Result<Unit> = runCatching {
        val response = userApi.deleteDeviceToken(token)
        if (!response.isSuccessful) {
            throw IllegalStateException(extractError(response.code(), response.errorBody()?.string(), "remove device token"))
        }
    }.recoverCatching { error ->
        throw IllegalStateException(mapNetworkError(error, "remove device token"))
    }

    private fun extractError(code: Int, raw: String?, action: String): String {
        val message = parseMessage(raw)
        return message ?: "Failed to $action ($code)"
    }

    private fun mapNetworkError(error: Throwable, action: String): String {
        return when (error) {
            is SocketTimeoutException -> "Request timed out while trying to $action"
            is IOException -> "Network error while trying to $action"
            else -> error.message ?: "Unable to $action"
        }
    }

    private fun parseMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            when {
                json.has("details") -> {
                    val details = json.optJSONArray("details")
                    if (details != null && details.length() > 0) details.optString(0) else null
                }
                json.has("message") -> json.optString("message")
                json.has("error") -> json.optString("error")
                else -> null
            }
        }.getOrNull()
    }
}
