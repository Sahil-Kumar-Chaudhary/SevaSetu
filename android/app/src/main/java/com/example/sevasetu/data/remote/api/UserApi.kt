package com.example.sevasetu.data.remote.api

import com.example.sevasetu.data.remote.dto.UpdateProfileRequest
import com.example.sevasetu.data.remote.dto.UserActivityResponse
import com.example.sevasetu.data.remote.dto.UserMeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Query

interface UserApi {
    @GET("/users/me")
    suspend fun getMe(): Response<UserMeResponse>

    @PATCH("/users/me")
    suspend fun updateMe(@Body request: UpdateProfileRequest): Response<UserMeResponse>

    @GET("/users/me/activity")
    suspend fun getMyActivity(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<UserActivityResponse>
}
