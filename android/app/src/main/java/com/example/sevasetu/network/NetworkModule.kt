package com.example.sevasetu.network

import android.content.Context
import com.example.sevasetu.data.remote.api.AuthApi
import com.example.sevasetu.data.remote.api.IssueApi
import com.example.sevasetu.data.remote.api.UserApi
import com.example.sevasetu.utils.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val BASE_URL = "https://sevasetu-zqa6.onrender.com/"

    fun provideAuthApi(context: Context): AuthApi {
        val retrofit = provideRetrofit(context)
        return retrofit.create(AuthApi::class.java)
    }

    fun provideIssueApi(context: Context): IssueApi {
        val retrofit = provideRetrofit(context)
        return retrofit.create(IssueApi::class.java)
    }

    fun provideUserApi(context: Context): UserApi {
        val retrofit = provideRetrofit(context)
        return retrofit.create(UserApi::class.java)
    }

    private fun provideRetrofit(context: Context): Retrofit {
        val tokenManager = TokenManager(context.applicationContext)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }
}
