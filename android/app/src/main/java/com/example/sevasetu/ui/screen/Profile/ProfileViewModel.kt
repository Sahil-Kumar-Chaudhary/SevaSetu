package com.example.sevasetu.ui.screen.Profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.sevasetu.data.remote.dto.UpdateProfileRequest
import com.example.sevasetu.data.remote.dto.UserActivityEventDto
import com.example.sevasetu.data.remote.dto.UserProfileDto
import com.example.sevasetu.data.repository.UserRepository
import com.example.sevasetu.utils.JurisdictionConstants
import com.example.sevasetu.utils.TokenManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: UserRepository,
    private val tokenManager: TokenManager
) : ViewModel() {
    private val _profileUiState = MutableStateFlow(ProfileUiState())
    val profileUiState: StateFlow<ProfileUiState> = _profileUiState.asStateFlow()

    private val _accountUiState = MutableStateFlow(AccountSettingsUiState())
    val accountUiState: StateFlow<AccountSettingsUiState> = _accountUiState.asStateFlow()

    private val _activityUiState = MutableStateFlow(MyActivityUiState())
    val activityUiState: StateFlow<MyActivityUiState> = _activityUiState.asStateFlow()

    fun loadProfile() {
        viewModelScope.launch {
            _profileUiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getMe()
                .onSuccess { user ->
                    _profileUiState.update {
                        it.copy(
                            isLoading = false,
                            userName = user.name?.ifBlank { "Citizen User" } ?: "Citizen User",
                            locationText = user.district?.name ?: user.jurisdiction?.name ?: "Location unavailable",
                            profileImageUrl = user.profileImageUrl
                        )
                    }
                }
                .onFailure { error ->
                    val expired = error.message?.contains("401") == true || error.message?.contains("403") == true
                    _profileUiState.update {
                        it.copy(isLoading = false, errorMessage = error.message, sessionExpired = expired)
                    }
                }
        }
    }

    fun loadAccountSettings() {
        viewModelScope.launch {
            _accountUiState.update { it.copy(isLoading = true, errorMessage = null, saveMessage = null) }
            repository.getMe()
                .onSuccess { user -> _accountUiState.value = user.toAccountState() }
                .onFailure { error ->
                    _accountUiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun saveAccountSettings() {
        val s = _accountUiState.value
        viewModelScope.launch {
            _accountUiState.update { it.copy(isSaving = true, errorMessage = null, saveMessage = null) }
            val req = UpdateProfileRequest(
                addressAreaType = s.addressAreaType,
                districtId = s.districtId,
                cityId = s.cityId,
                wardId = s.wardId,
                blockId = s.blockId,
                panchayatId = s.panchayatId,
                phone = s.phone,
                addressText = s.addressText,
                pinCode = s.pinCode,
                addressLocality = s.addressLocality,
                addressLandmark = s.addressLandmark,
                addressLat = s.addressLat.toDoubleOrNull(),
                addressLng = s.addressLng.toDoubleOrNull(),
                profileImageUrl = s.profileImageUrl.ifBlank { null }
            )
            repository.updateMe(req)
                .onSuccess { user ->
                    _accountUiState.value = user.toAccountState().copy(saveMessage = "Saved successfully")
                }
                .onFailure { error ->
                    _accountUiState.update { it.copy(isSaving = false, errorMessage = error.message) }
                }
        }
    }

    fun loadActivity() {
        viewModelScope.launch {
            _activityUiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.getMyActivity()
                .onSuccess { data ->
                    _activityUiState.update { it.copy(isLoading = false, events = data.events) }
                }
                .onFailure { error ->
                    _activityUiState.update { it.copy(isLoading = false, errorMessage = error.message) }
                }
        }
    }

    fun onPhoneChanged(value: String) = _accountUiState.update { it.copy(phone = value) }
    fun onAddressTextChanged(value: String) = _accountUiState.update { it.copy(addressText = value) }
    fun onPinCodeChanged(value: String) = _accountUiState.update { it.copy(pinCode = value) }
    fun onAddressLocalityChanged(value: String) = _accountUiState.update { it.copy(addressLocality = value) }
    fun onAddressLandmarkChanged(value: String) = _accountUiState.update { it.copy(addressLandmark = value) }
    fun onAddressLatChanged(value: String) = _accountUiState.update { it.copy(addressLat = value) }
    fun onAddressLngChanged(value: String) = _accountUiState.update { it.copy(addressLng = value) }
    fun onProfileImageUrlChanged(value: String) = _accountUiState.update { it.copy(profileImageUrl = value) }

    fun onDistrictChanged(districtName: String) {
        val district = JurisdictionConstants.DISTRICTS.firstOrNull { it.name == districtName } ?: return
        val areaType = JurisdictionConstants.getCategory(district.id)
        val urban = JurisdictionConstants.getUrbanLocation(district.id)
        val rural = JurisdictionConstants.getRuralLocation(district.id)
        _accountUiState.update {
            it.copy(
                districtId = district.id,
                districtName = district.name,
                addressAreaType = areaType,
                cityId = urban?.cityId,
                cityName = urban?.cityName,
                wardId = urban?.wardId,
                wardName = urban?.wardName,
                blockId = rural?.blockId,
                blockName = rural?.blockName,
                panchayatId = rural?.panchayatId,
                panchayatName = rural?.panchayatName
            )
        }
    }

    fun logout() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                viewModelScope.launch {
                    if (token.isNotBlank()) {
                        repository.removeDeviceToken(token)
                    }
                    tokenManager.clear()
                    _profileUiState.value = ProfileUiState(sessionExpired = true)
                }
            }
            .addOnFailureListener {
                tokenManager.clear()
                _profileUiState.value = ProfileUiState(sessionExpired = true)
            }
    }
}

data class ProfileUiState(
    val isLoading: Boolean = false,
    val userName: String = "Citizen User",
    val locationText: String = "Location unavailable",
    val profileImageUrl: String? = null,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false
)

data class AccountSettingsUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val addressText: String = "",
    val pinCode: String = "",
    val addressLocality: String = "",
    val addressLandmark: String = "",
    val addressLat: String = "",
    val addressLng: String = "",
    val profileImageUrl: String = "",
    val addressAreaType: String = "",
    val districtId: String? = null,
    val districtName: String = "",
    val cityId: String? = null,
    val cityName: String? = null,
    val wardId: String? = null,
    val wardName: String? = null,
    val blockId: String? = null,
    val blockName: String? = null,
    val panchayatId: String? = null,
    val panchayatName: String? = null,
    val errorMessage: String? = null,
    val saveMessage: String? = null
)

data class MyActivityUiState(
    val isLoading: Boolean = false,
    val events: List<UserActivityEventDto> = emptyList(),
    val errorMessage: String? = null
)

private fun UserProfileDto.toAccountState(): AccountSettingsUiState {
    val districtIdValue = jurisdictionIds?.districtId ?: address?.addressDistrict
    val matchedDistrict = districtIdValue?.let { id ->
        JurisdictionConstants.DISTRICTS.firstOrNull { districtItem -> districtItem.id == id }
    }
    val districtNameValue = district?.name ?: matchedDistrict?.name.orEmpty()
    val area = address?.addressAreaType ?: matchedDistrict?.let { JurisdictionConstants.getCategory(it.id) }.orEmpty()
    val urban = districtIdValue?.let { JurisdictionConstants.getUrbanLocation(it) }
    val rural = districtIdValue?.let { JurisdictionConstants.getRuralLocation(it) }

    return AccountSettingsUiState(
        isLoading = false,
        name = name.orEmpty(),
        email = email.orEmpty(),
        phone = phone.orEmpty(),
        addressText = address?.addressText.orEmpty(),
        pinCode = address?.pinCode.orEmpty(),
        addressLocality = address?.addressLocality.orEmpty(),
        addressLandmark = address?.addressLandmark.orEmpty(),
        addressLat = address?.addressLat?.toString().orEmpty(),
        addressLng = address?.addressLng?.toString().orEmpty(),
        profileImageUrl = profileImageUrl.orEmpty(),
        addressAreaType = area,
        districtId = districtIdValue,
        districtName = districtNameValue,
        cityId = urban?.cityId,
        cityName = urban?.cityName,
        wardId = urban?.wardId,
        wardName = urban?.wardName,
        blockId = rural?.blockId,
        blockName = rural?.blockName,
        panchayatId = rural?.panchayatId,
        panchayatName = rural?.panchayatName
    )
}

class ProfileViewModelFactory(
    private val repository: UserRepository,
    private val tokenManager: TokenManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            return ProfileViewModel(repository, tokenManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
