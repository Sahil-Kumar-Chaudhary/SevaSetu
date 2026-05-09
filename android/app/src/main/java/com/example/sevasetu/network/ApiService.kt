package com.example.sevasetu.network

import android.content.Context
import com.example.sevasetu.data.remote.api.AuthApi
import com.example.sevasetu.data.remote.api.IssueApi
import com.example.sevasetu.data.remote.api.UserApi

object ApiService {
    fun authApi(context: Context): AuthApi = NetworkModule.provideAuthApi(context)
    fun issueApi(context: Context): IssueApi = NetworkModule.provideIssueApi(context)
    fun userApi(context: Context): UserApi = NetworkModule.provideUserApi(context)
}
