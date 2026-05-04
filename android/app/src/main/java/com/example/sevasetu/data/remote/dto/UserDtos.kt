package com.example.sevasetu.data.remote.dto

import com.google.gson.annotations.SerializedName

data class NamedLocationDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null
)

data class UserAddressDto(
    @SerializedName("addressAreaType") val addressAreaType: String? = null,
    @SerializedName("addressDistrict") val addressDistrict: String? = null,
    @SerializedName("addressCityOrPanchayat") val addressCityOrPanchayat: String? = null,
    @SerializedName("addressWard") val addressWard: String? = null,
    @SerializedName("addressLocality") val addressLocality: String? = null,
    @SerializedName("addressLandmark") val addressLandmark: String? = null,
    @SerializedName("addressText") val addressText: String? = null,
    @SerializedName("pinCode") val pinCode: String? = null,
    @SerializedName("addressLat") val addressLat: Double? = null,
    @SerializedName("addressLng") val addressLng: Double? = null
)

data class JurisdictionIdsDto(
    @SerializedName("districtId") val districtId: String? = null,
    @SerializedName("cityOrPanchayatId") val cityOrPanchayatId: String? = null,
    @SerializedName("wardId") val wardId: String? = null,
    @SerializedName("jurisdictionId") val jurisdictionId: String? = null
)

data class UserProfileDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName(value = "profileImageUrl", alternate = ["profileImageURL", "profile_image_url", "avatarUrl", "avatar"]) val profileImageUrl: String? = null,
    @SerializedName("district") val district: NamedLocationDto? = null,
    @SerializedName("jurisdiction") val jurisdiction: NamedLocationDto? = null,
    @SerializedName("address") val address: UserAddressDto? = null,
    @SerializedName("jurisdictionIds") val jurisdictionIds: JurisdictionIdsDto? = null
)

data class UserMeResponse(
    @SerializedName("user") val user: UserProfileDto? = null
)

data class UpdateProfileRequest(
    @SerializedName("addressAreaType") val addressAreaType: String? = null,
    @SerializedName("districtId") val districtId: String? = null,
    @SerializedName("cityId") val cityId: String? = null,
    @SerializedName("wardId") val wardId: String? = null,
    @SerializedName("blockId") val blockId: String? = null,
    @SerializedName("panchayatId") val panchayatId: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("addressText") val addressText: String? = null,
    @SerializedName("pinCode") val pinCode: String? = null,
    @SerializedName("addressLocality") val addressLocality: String? = null,
    @SerializedName("addressLandmark") val addressLandmark: String? = null,
    @SerializedName("addressLat") val addressLat: Double? = null,
    @SerializedName("addressLng") val addressLng: Double? = null,
    @SerializedName("profileImageUrl") val profileImageUrl: String? = null
)

data class UserActivityIssueDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("currentStatus") val currentStatus: String? = null
)

data class UserActivityEventDto(
    @SerializedName("eventId") val eventId: String? = null,
    @SerializedName("eventType") val eventType: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("issue") val issue: UserActivityIssueDto? = null
)

data class UserActivityResponse(
    @SerializedName("page") val page: Int? = null,
    @SerializedName("limit") val limit: Int? = null,
    @SerializedName("events") val events: List<UserActivityEventDto> = emptyList()
)
