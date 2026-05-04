package com.example.sevasetu.ui.screen.Profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.sevasetu.Dashboard
import com.example.sevasetu.Login
import com.example.sevasetu.data.remote.dto.UserActivityEventDto
import com.example.sevasetu.data.repository.UserRepository
import com.example.sevasetu.network.NetworkModule
import com.example.sevasetu.ui.screen.Alerts.AlertsScreen
import com.example.sevasetu.ui.screen.Reports.ReportScreen
import com.example.sevasetu.ui.theme.SevaSetuTheme
import com.example.sevasetu.utils.JurisdictionConstants
import com.example.sevasetu.utils.TokenManager
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private enum class ProfileSheet {
    MY_ACTIVITY,
    ACCOUNT_SETTINGS
}

private val ProfileGreen = Color(0xFF00875A)
private val ProfileGreenSoft = Color(0xFFF7FBF8)
private val ProfileBorder = Color(0xFFE1EBE5)
private val ProfileText = Color(0xFF17231D)
private val ProfileMuted = Color(0xFF6E7C73)
private const val PROFILE_IMAGE_BASE_URL = "https://sevasetu-zqa6.onrender.com/"

class ProfileScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SevaSetuTheme {
                ProfileScreenContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenContent() {
    val context = LocalContext.current
    val viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModelFactory(
            repository = UserRepository(NetworkModule.provideUserApi(context)),
            tokenManager = TokenManager(context)
        )
    )
    val uiState by viewModel.profileUiState.collectAsState()
    val accountState by viewModel.accountUiState.collectAsState()
    val activityState by viewModel.activityUiState.collectAsState()
    var activeSheet by remember { mutableStateOf<ProfileSheet?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            val intent = Intent(context, Login::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SevaSetu",
                        color = ProfileGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9))
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp),
                                tint = ProfileGreen
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = false,
                    onClick = { context.startActivity(Intent(context, Dashboard::class.java)) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("HOME") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { context.startActivity(Intent(context, ReportScreen::class.java)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = "Reports") },
                    label = { Text("REPORTS") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { context.startActivity(Intent(context, AlertsScreen::class.java)) },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Alerts") },
                    label = { Text("ALERTS") }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("PROFILE") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ProfileGreen,
                        selectedTextColor = ProfileGreen,
                        indicatorColor = Color(0xFFE8F5E9)
                    )
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color(0xFFF2F5F3))
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, ProfileBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ProfileGreenSoft)
                                .padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(40.dp),
                                color = Color(0xFFE7F5EA),
                                modifier = Modifier.size(96.dp)
                            ) {
                                val profileImageUrl = uiState.profileImageUrl.normalizeProfileImageUrl()
                                if (profileImageUrl != null) {
                                    AsyncImage(
                                        model = profileImageUrl,
                                        contentDescription = "Profile Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(18.dp),
                                        tint = Color(0xFF7EA891)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Profile Details",
                                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                        color = ProfileGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE8F5E9)) {
                                        Text(
                                            text = "VERIFIED MEMBER",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ProfileGreen
                                        )
                                    }
                                }
                                Text(
                                    text = uiState.userName,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                                    color = ProfileText,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = uiState.locationText,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                    color = ProfileMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (uiState.isLoading) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = ProfileGreen
                                        )
                                        Text("Loading profile...", color = ProfileMuted, fontSize = 12.sp)
                                    }
                                }
                                if (!uiState.errorMessage.isNullOrBlank()) {
                                    Text(
                                        text = uiState.errorMessage.orEmpty(),
                                        color = Color(0xFFB3261E),
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    OutlinedButton(
                                        onClick = viewModel::loadProfile,
                                        modifier = Modifier.height(38.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProfileSectionTitle("Quick Actions")
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = ProfileGreenSoft,
                                border = BorderStroke(1.dp, ProfileBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ProfileInfoBadge("Profile synced", ProfileGreenSoft, ProfileGreen)
                                        ProfileInfoBadge("Ready for updates", Color.White, ProfileMuted, border = ProfileBorder)
                                    }
                                    Text(
                                        text = "Your profile and account settings are pulled from the server and shown.",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                        color = ProfileMuted
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.logout() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFEBEE)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Logout", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "PREFERENCES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, ProfileBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        PreferenceItem(
                            icon = Icons.Default.Analytics,
                            label = "My Activity",
                            extraText = "Timeline view",
                            onClick = {
                                activeSheet = ProfileSheet.MY_ACTIVITY
                                viewModel.loadActivity()
                            }
                        )
                        HorizontalDivider(color = ProfileBorder)
                        PreferenceItem(
                            icon = Icons.Default.Settings,
                            label = "Account Settings",
                            extraText = "Manage contact details",
                            onClick = {
                                activeSheet = ProfileSheet.ACCOUNT_SETTINGS
                                viewModel.loadAccountSettings()
                            }
                        )
                        HorizontalDivider(color = ProfileBorder)
                        PreferenceItem(icon = Icons.Default.Language, label = "Language", extraText = "English")
                        HorizontalDivider(color = ProfileBorder)
                        PreferenceItem(icon = Icons.AutoMirrored.Filled.HelpOutline, label = "Help & Support")
                        HorizontalDivider(color = ProfileBorder)
                        PreferenceItem(icon = Icons.Default.DarkMode, label = "Dark Mode", isSwitch = true)
                    }
                }
            }

            item {
                Text(
                    text = "VERSION ${readAppVersionName()}",
                    fontSize = 10.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    val sheet = activeSheet
    if (sheet != null) {
        Dialog(
            onDismissRequest = { activeSheet = null },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.88f),
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    when (sheet) {
                        ProfileSheet.MY_ACTIVITY -> MyActivitySheetContent(activityState = activityState)
                        ProfileSheet.ACCOUNT_SETTINGS -> AccountSettingsSheetContent(
                            state = accountState,
                            onDistrictChanged = viewModel::onDistrictChanged,
                            onPhoneChanged = viewModel::onPhoneChanged,
                            onAddressTextChanged = viewModel::onAddressTextChanged,
                            onPinCodeChanged = viewModel::onPinCodeChanged,
                            onAddressLocalityChanged = viewModel::onAddressLocalityChanged,
                            onAddressLandmarkChanged = viewModel::onAddressLandmarkChanged,
                            onAddressLatChanged = viewModel::onAddressLatChanged,
                            onAddressLngChanged = viewModel::onAddressLngChanged,
                            onProfileImageUrlChanged = viewModel::onProfileImageUrlChanged,
                            onSave = viewModel::saveAccountSettings
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PreferenceItem(
    icon: ImageVector,
    label: String,
    extraText: String? = null,
    isSwitch: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    var checked by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = ProfileGreenSoft,
            border = BorderStroke(1.dp, ProfileBorder)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp),
                tint = ProfileGreen
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = ProfileText)
            if (extraText != null) {
                Text(
                    text = extraText,
                    fontSize = 12.sp,
                    color = ProfileMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isSwitch) {
            Switch(
                checked = checked,
                onCheckedChange = { checked = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ProfileGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.LightGray
                )
            )
        } else {
            IconButton(onClick = { onClick?.invoke() }) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    SevaSetuTheme { ProfileScreenContent() }
}

@Composable
private fun MyActivitySheetContent(activityState: MyActivityUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Activity",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = ProfileText,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { /* Dismiss is handled by parent Dialog */ }) {
                Icon(Icons.Default.Info, contentDescription = "Close", tint = Color.LightGray)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ProfileGreenSoft,
            border = BorderStroke(1.dp, ProfileBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    activityState.isLoading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = ProfileGreen
                            )
                            Text("Loading activity...", color = ProfileMuted)
                        }
                    }

                    !activityState.errorMessage.isNullOrBlank() -> {
                        Text(activityState.errorMessage, color = Color(0xFFB3261E))
                    }

                    activityState.events.isEmpty() -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = ProfileMuted, modifier = Modifier.size(18.dp))
                            Text("No activity found.", color = ProfileMuted)
                        }
                    }

                    else -> {
                        val events = activityState.events.take(20)
                        events.forEachIndexed { index, event ->
                            ProfileTimelineItem(
                                event = event,
                                isLast = index == events.lastIndex
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSettingsSheetContent(
    state: AccountSettingsUiState,
    onDistrictChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onAddressTextChanged: (String) -> Unit,
    onPinCodeChanged: (String) -> Unit,
    onAddressLocalityChanged: (String) -> Unit,
    onAddressLandmarkChanged: (String) -> Unit,
    onAddressLatChanged: (String) -> Unit,
    onAddressLngChanged: (String) -> Unit,
    onProfileImageUrlChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    var districtMenuExpanded by remember { mutableStateOf(false) }
    val districtOptions = remember { JurisdictionConstants.DISTRICTS.map { it.name } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Account Settings",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = ProfileText,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { /* Dismiss is handled by parent Dialog */ }) {
                Icon(Icons.Default.Info, contentDescription = "Close", tint = Color.LightGray)
            }
        }

        if (state.isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ProfileGreen)
                Text("Loading account details...", color = ProfileMuted)
            }
        }

        ProfileSectionCard(title = "Identity") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileInfoRow(label = "Name", value = state.name.ifBlank { "N/A" })
                ProfileInfoRow(label = "Email", value = state.email.ifBlank { "N/A" })
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = onPhoneChanged,
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.profileImageUrl,
                    onValueChange = onProfileImageUrlChanged,
                    label = { Text("Profile Image URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        ProfileSectionCard(title = "Address") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(
                    expanded = districtMenuExpanded,
                    onExpandedChange = { districtMenuExpanded = !districtMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = state.districtName.ifBlank { "Select district" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("District") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = districtMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = districtMenuExpanded,
                        onDismissRequest = { districtMenuExpanded = false }
                    ) {
                        districtOptions.forEach { districtName ->
                            DropdownMenuItem(
                                text = { Text(districtName) },
                                onClick = {
                                    onDistrictChanged(districtName)
                                    districtMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.addressText,
                    onValueChange = onAddressTextChanged,
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.addressLocality,
                    onValueChange = onAddressLocalityChanged,
                    label = { Text("Locality") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.addressLandmark,
                    onValueChange = onAddressLandmarkChanged,
                    label = { Text("Landmark") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.pinCode,
                        onValueChange = onPinCodeChanged,
                        label = { Text("Pin Code") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.addressAreaType,
                        onValueChange = { },
                        enabled = false,
                        label = { Text("Area Type") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.addressLat,
                        onValueChange = onAddressLatChanged,
                        label = { Text("Latitude") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.addressLng,
                        onValueChange = onAddressLngChanged,
                        label = { Text("Longitude") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        ProfileSectionCard(title = "Location Summary") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileInfoRow(label = "Area Type", value = state.addressAreaType.ifBlank { "N/A" })
                ProfileInfoRow(label = "District", value = state.districtName.ifBlank { state.districtId ?: "N/A" })
                ProfileInfoRow(label = "District ID", value = state.districtId ?: "N/A")
                if (state.addressAreaType == "URBAN") {
                    ProfileInfoRow(label = "City", value = state.cityName ?: "N/A")
                    ProfileInfoRow(label = "City ID", value = state.cityId ?: "N/A")
                    ProfileInfoRow(label = "Ward", value = state.wardName ?: "N/A")
                    ProfileInfoRow(label = "Ward ID", value = state.wardId ?: "N/A")
                } else if (state.addressAreaType == "RURAL") {
                    ProfileInfoRow(label = "Block", value = state.blockName ?: "N/A")
                    ProfileInfoRow(label = "Block ID", value = state.blockId ?: "N/A")
                    ProfileInfoRow(label = "Panchayat", value = state.panchayatName ?: "N/A")
                    ProfileInfoRow(label = "Panchayat ID", value = state.panchayatId ?: "N/A")
                }
            }
        }

        if (!state.errorMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFEBEE),
                border = BorderStroke(1.dp, Color(0xFFFFCDD2))
            ) {
                Text(
                    state.errorMessage,
                    color = Color(0xFFB3261E),
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }
        }

        if (!state.saveMessage.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE8F5E9),
                border = BorderStroke(1.dp, ProfileBorder)
            ) {
                Text(
                    state.saveMessage,
                    color = ProfileGreen,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        OutlinedButton(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (state.isSaving) "Saving..." else "Save Changes", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ProfileSectionTitle(title: String) {
    Text(
        text = title,
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        color = ProfileText,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ProfileSectionCard(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProfileSectionTitle(title)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ProfileGreenSoft,
            border = BorderStroke(1.dp, ProfileBorder)
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = ProfileMuted,
            modifier = Modifier.width(104.dp)
        )
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = ProfileText,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileInfoBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    border: Color? = null
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor,
        border = border?.let { BorderStroke(1.dp, it) }
    ) {
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileTimelineItem(event: UserActivityEventDto, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            val icon = when (event.eventType?.uppercase(Locale.ROOT)) {
                "CREATED", "POSTED" -> Icons.Default.RadioButtonChecked
                "RESOLVED", "CLOSED", "APPROVED" -> Icons.Default.CheckCircle
                else -> Icons.Default.Info
            }
            Icon(imageVector = icon, contentDescription = null, tint = ProfileGreen, modifier = Modifier.size(16.dp))
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(56.dp)
                        .background(ProfileBorder)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.title?.takeIf { it.isNotBlank() }
                        ?: event.eventType?.toDisplayLabel("Activity")
                        ?: "Activity",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = ProfileText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatProfileDate(event.createdAt),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = ProfileMuted
                )
            }
            if (!event.eventType.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                ProfileInfoBadge(
                    text = event.eventType.toDisplayLabel("Activity"),
                    backgroundColor = Color(0xFFE8F5E9),
                    textColor = ProfileGreen
                )
            }
            if (!event.message.isNullOrBlank()) {
                Text(
                    text = event.message,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = ProfileText,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            event.issue?.let { issue ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, ProfileBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = issue.title ?: "Related issue",
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            color = ProfileText,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        ProfileInfoRow(label = "Issue ID", value = issue.id?.toString() ?: "N/A")
                        ProfileInfoRow(label = "Status", value = issue.currentStatus ?: "N/A")
                    }
                }
            }
        }
    }
}

private fun String?.toDisplayLabel(fallback: String): String {
    val value = this?.trim()?.takeIf(String::isNotEmpty) ?: return fallback
    return value
        .lowercase(Locale.ROOT)
        .split('_', ' ')
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }
}

private fun formatProfileDate(dateString: String?): String {
    if (dateString.isNullOrBlank()) return "N/A"
    val displayFormat = SimpleDateFormat("dd/MM/yy, hh:mm a", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }
    val inputFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    ).onEach { it.timeZone = TimeZone.getDefault() }

    for (format in inputFormats) {
        try {
            val parsed = format.parse(dateString)
            if (parsed != null) return displayFormat.format(parsed)
        } catch (_: ParseException) {
            // Try next.
        }
    }

    return dateString
}

private fun String?.normalizeProfileImageUrl(): String? {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return null
    if (
        raw.startsWith("http://", ignoreCase = true) ||
        raw.startsWith("https://", ignoreCase = true) ||
        raw.startsWith("data:image/", ignoreCase = true) ||
        raw.startsWith("content://", ignoreCase = true) ||
        raw.startsWith("file://", ignoreCase = true)
    ) {
        return raw
    }
    // If user stored a host/path without scheme, prefer https.
    if (raw.contains(".") && !raw.contains(" ")) {
        return "https://${raw.trimStart('/')}"
    }
    return PROFILE_IMAGE_BASE_URL.trimEnd('/') + "/" + raw.trimStart('/')
}

private fun readAppVersionName(): String {
    return runCatching {
        val buildConfigClass = Class.forName("com.example.sevasetu.BuildConfig")
        buildConfigClass.getField("VERSION_NAME").get(null) as? String
    }.getOrNull().orEmpty().ifBlank { "unknown" }
}
