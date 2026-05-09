package com.example.sevasetu.network

import com.example.sevasetu.utils.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenManager.getToken()

        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) {
                addHeader("Authorization", "Bearer $token")
            }
        }.build()

        val response = chain.proceed(request)
        if (response.code == 401) {
            tokenManager.clear()
            UnauthorizedEventBus.notifyUnauthorized()
        }
        return response
    }
}
