package com.example.sevasetu

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.Manifest
import androidx.annotation.RequiresApi
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import android.location.Location
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.sevasetu.data.remote.dto.DashboardResponse
import com.example.sevasetu.data.remote.dto.IssueDto
import com.example.sevasetu.data.remote.dto.TimelineUpdateDto
import com.example.sevasetu.data.repository.IssueRepository
import com.example.sevasetu.network.ApiService
import com.example.sevasetu.ui.common.IssueDetailModal
import com.example.sevasetu.ui.screen.Alerts.AlertsScreen
import com.example.sevasetu.ui.screen.Profile.ProfileScreen
import com.example.sevasetu.ui.screen.Reports.ReportScreen
import com.example.sevasetu.ui.theme.SevaSetuTheme
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration as OsmConfiguration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.graphics.Color as AndroidColor
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.graphics.createBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import com.example.sevasetu.ui.screen.Reports.IssueReport
import com.example.sevasetu.utils.ThemePreferenceManager
import com.example.sevasetu.notifications.FcmTokenRegistrar
import com.example.sevasetu.notifications.NotificationSupport
import com.example.sevasetu.notifications.NotificationPermissionHelper

class Dashboard : ComponentActivity() {
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("Dashboard", "Notification permission granted")
        } else {
            android.util.Log.w("Dashboard", "Notification permission denied by user")
        }
    }

    private val requestLocationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            android.util.Log.d("Dashboard", "Location permission granted")
        } else {
            android.util.Log.w("Dashboard", "Location permission denied by user")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize notification system
        NotificationSupport.createChannels(this)
        FcmTokenRegistrar.registerCurrentToken(this)

        // Request permission and log status
        requestNotificationPermissionIfNeeded()
        requestLocationPermissionIfNeeded()
        NotificationPermissionHelper.logPermissionStatus(this)

        val themePreferenceManager = ThemePreferenceManager(this)
        enableEdgeToEdge()
        setContent {
            val themePreference = remember { themePreferenceManager.getTheme() }
            SevaSetuTheme(themePreference = themePreference) {
                DashboardScreen()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (NotificationPermissionHelper.needsPermissionRequest(this)) {
            android.util.Log.d("Dashboard", "Requesting POST_NOTIFICATIONS permission")
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            android.util.Log.d("Dashboard", "Requesting location permissions")
            requestLocationPermissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val issueRepository = remember { IssueRepository(ApiService.issueApi(context)) }
    val scope = rememberCoroutineScope()
    var mapUiState by remember { mutableStateOf<MapUiState>(MapUiState.Loading) }
    var dashboardUiState by remember { mutableStateOf<DashboardUiState>(DashboardUiState.Loading) }
    var currentLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var selectedIssue by remember { mutableStateOf<IssueDto?>(null) }
    var voteInFlight by remember { mutableStateOf(false) }
    var selectedIssueTimeline by remember { mutableStateOf<List<TimelineUpdateDto>?>(null) }
    var isTimelineLoading by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIssue?.id) {
        val issueId = selectedIssue?.id
        if (issueId != null) {
            isTimelineLoading = true
            selectedIssueTimeline = null
            issueRepository.getIssueTimeline(issueId)
                .onSuccess { response ->
                    selectedIssueTimeline = response.timeline
                }
                .onFailure {
                    // Fail silently or log
                }
            isTimelineLoading = false
        }
    }

    val handleVote: (IssueDto) -> Unit = { issue ->
        if (!voteInFlight) {
            voteInFlight = true
            scope.launch {
                issueRepository.voteIssue(issue.id)
                    .onSuccess { response ->
                        // Update local vote tracker
                        val tokenManager = com.example.sevasetu.utils.TokenManager(context)
                        if (response.voted) {
                            tokenManager.addVotedIssue(issue.id)
                        } else {
                            tokenManager.removeVotedIssue(issue.id)
                        }

                        // Update selectedIssue to reflect changes in modal
                        if (selectedIssue?.id == issue.id) {
                            selectedIssue = selectedIssue?.copy(
                                voteCount = response.totalVotes,
                                isVotedByMe = response.voted
                            )
                        }

                        // Also update it in the map lists
                        if (mapUiState is MapUiState.Success) {
                            val success = mapUiState as MapUiState.Success
                            val updatedFullIssues = success.fullIssues.map {
                                if (it.id == issue.id) it.copy(
                                    voteCount = response.totalVotes,
                                    isVotedByMe = response.voted
                                ) else it
                            }
                            mapUiState = success.copy(fullIssues = updatedFullIssues)
                        }
                    }
                    .onFailure {
                        android.widget.Toast.makeText(
                            context,
                            "Failed to update vote: ${it.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                voteInFlight = false
            }
        }
    }

    // Get user's district from TokenManager
    val userDistrictId = remember {
        val tokenManager = com.example.sevasetu.utils.TokenManager(context)
        tokenManager.getUserDistrict()?.takeIf { it.isNotBlank() } ?: DEFAULT_NEARBY_DISTRICT_ID
    }

    val userDistrictName = remember(userDistrictId) {
        com.example.sevasetu.utils.JurisdictionConstants.DISTRICTS
            .find { it.id == userDistrictId }?.name ?: "Unknown"
    }

    val userDistrictCoords = remember(userDistrictId) {
        val dist = com.example.sevasetu.utils.JurisdictionConstants.DISTRICTS
            .find { it.id == userDistrictId }
        if (dist != null) GeoPoint(dist.lat, dist.lng) else GeoPoint(31.6340, 74.8723)
    }

    val mapCenterPoint = remember(currentLocation, userDistrictCoords) {
        currentLocation?.let { GeoPoint(it.first, it.second) } ?: userDistrictCoords
    }

    val fetchCurrentLocation: (callback: (Pair<Double, Double>?) -> Unit) -> Unit = { callback ->
        try {
            val hasLocationPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

            if (hasLocationPermission) {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val tokenSource = CancellationTokenSource()
                fusedLocationClient
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            callback(Pair(location.latitude, location.longitude))
                        } else {
                            fusedLocationClient.lastLocation
                                .addOnSuccessListener { lastLocation: Location? ->
                                    callback(lastLocation?.let { Pair(it.latitude, it.longitude) })
                                }
                                .addOnFailureListener {
                                    callback(null)
                                }
                        }
                    }
                    .addOnFailureListener {
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { lastLocation: Location? ->
                                callback(lastLocation?.let { Pair(it.latitude, it.longitude) })
                            }
                            .addOnFailureListener {
                                callback(null)
                            }
                    }
            } else {
                callback(null)
            }
        } catch (_: Exception) {
            callback(null)
        }
    }

    val fetchNearbyIssues: () -> Unit = {
        scope.launch {
            mapUiState = MapUiState.Loading

            // Try to get current location first
            fetchCurrentLocation { location ->
                scope.launch {
                    currentLocation = location
                    issueRepository.getNearbyIssues(
                        lat = location?.first,
                        lng = location?.second,
                        radiusKm = 5.0,
                        districtId = userDistrictId
                    )
                        .onSuccess { response ->
                            val tokenManager = com.example.sevasetu.utils.TokenManager(context)
                            val votedSet = tokenManager.getVotedIssues()
                            val enrichedIssues = response.issues.map {
                                it.copy(isVotedByMe = it.isVotedByMe ?: votedSet.contains(it.id))
                            }
                            
                            val mapIssues = enrichedIssues.mapNotNull { it.toMapIssue() }
                            val searchMode = response.searchMode
                            val displayInfo = when {
                                searchMode == "location" && response.location != null -> {
                                    "Location-based search (${response.location.radiusKm}km)"
                                }
                                searchMode == "district" && response.district != null -> {
                                    "District-based search (${response.district.name})"
                                }
                                else -> "Nearby Issues"
                            }
                            mapUiState = MapUiState.Success(
                                mapIssues,
                                displayInfo,
                                searchMode,
                                enrichedIssues
                            )
                        }
                        .onFailure { throwable ->
                            mapUiState = MapUiState.Error(
                                throwable.message ?: "Unable to load nearby issues"
                            )
                        }
                }
            }
        }
    }

    val fetchDashboard: () -> Unit = {
        scope.launch {
            dashboardUiState = DashboardUiState.Loading

            fetchCurrentLocation { location ->
                scope.launch {
                    currentLocation = location
                    issueRepository.getDashboard(
                        lat = location?.first,
                        lng = location?.second,
                        radiusKm = 5.0,
                        districtId = userDistrictId,
                        insightWindowDays = 30
                    )
                        .onSuccess { response ->
                            dashboardUiState = DashboardUiState.Success(response)
                        }
                        .onFailure { throwable ->
                            dashboardUiState = DashboardUiState.Error(
                                throwable.message ?: "Unable to load dashboard"
                            )
                        }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchNearbyIssues()
        fetchDashboard()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SevaSetu",
                        color = MaterialTheme.colorScheme.primary,
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
                    IconButton(onClick = {
                        context.startActivity(Intent(context, ProfileScreen::class.java))
                    }) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            // Placeholder for profile image
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Profile",
                                modifier = Modifier.fillMaxSize(),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("HOME") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        context.startActivity(Intent(context, ReportScreen::class.java))
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = "Reports") },
                    label = { Text("REPORTS") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        context.startActivity(Intent(context, AlertsScreen::class.java))
                    },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Alerts") },
                    label = { Text("ALERTS") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        context.startActivity(Intent(context, ProfileScreen::class.java))
                    },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("PROFILE") }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    context.startActivity(Intent(context, IssueReport::class.java))
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Report Issue")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Search Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Search for locations or report...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.0f)
                    )
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(16.dp))

            DashboardMapSection(
                mapUiState = mapUiState,
                userDistrictName = userDistrictName,
                centerPoint = mapCenterPoint,
                onRetry = fetchNearbyIssues
            )

            Spacer(Modifier.height(16.dp))

            DashboardSnapshotSection(
                dashboardUiState = dashboardUiState,
                onOpenReports = {
                    context.startActivity(Intent(context, ReportScreen::class.java))
                }
            )

            Spacer(Modifier.height(16.dp))

            // Nearby Insights
            Text(
                "Nearby Insights",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            val currentDashboardState = dashboardUiState
            when (currentDashboardState) {
                is DashboardUiState.Success -> {
                    val insights = currentDashboardState.data.nearbyInsights
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("OPEN", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                Text(insights.open.toString(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("IN_PROGRESS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                Text(insights.inProgress.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("CLOSED", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                Text(insights.closed.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Quick Categories
            Text(
                "Quick Categories",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { CategoryChip("Garbage", Icons.Default.Delete) }
                item { CategoryChip("Roads", Icons.Default.EditRoad) }
                item { CategoryChip("Water", Icons.Default.WaterDrop) }
            }

            Spacer(Modifier.height(24.dp))

            // Nearby Issues
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Nearby Issues",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = { }) {
                    Text("View All", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, size = 16.sp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            NearbyIssuesListSection(
                mapUiState = mapUiState,
                onRetry = fetchNearbyIssues,
                onIssueClick = { selectedIssue = it }
            )
        }
    }

    // Show issue detail modal
    if (selectedIssue != null) {
        IssueDetailModal(
            issue = selectedIssue!!,
            onDismiss = { selectedIssue = null },
            onVoteClick = { handleVote(selectedIssue!!) },
            isVoteLoading = voteInFlight,
            timeline = selectedIssueTimeline,
            isTimelineLoading = isTimelineLoading
        )
    }

}

@Composable
private fun DashboardSnapshotSection(
    dashboardUiState: DashboardUiState,
    onOpenReports: () -> Unit
) {
    when (dashboardUiState) {
        DashboardUiState.Loading -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        is DashboardUiState.Error -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = dashboardUiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        is DashboardUiState.Success -> {
            val data = dashboardUiState.data
            val snapshot = data.myReportsSnapshot
            val pending = data.myPendingAction
            val risk = data.nearbyRiskSummary

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "My Reports Snapshot",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SnapshotStatChip(
                            label = "OPEN",
                            count = snapshot.open,
                            background = MaterialTheme.colorScheme.errorContainer,
                            textColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        SnapshotStatChip(
                            label = "IN_PROGRESS",
                            count = snapshot.inProgress,
                            background = MaterialTheme.colorScheme.secondaryContainer,
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SnapshotStatChip(
                            label = "RESOLVED",
                            count = snapshot.resolved,
                            background = MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        SnapshotStatChip(
                            label = "REJECTED",
                            count = snapshot.rejected,
                            background = MaterialTheme.colorScheme.errorContainer,
                            textColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "My Pending Action",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "You have ${pending.unresolved} unresolved reports",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            TextButton(onClick = onOpenReports) {
                                Text(pending.cta.label)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Nearby Risk Summary",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${risk.highPriority} high-priority reports",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${risk.open} open reports in your area",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Coverage: ${risk.coverageText}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotStatChip(
    label: String,
    count: Int,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
private fun NearbyIssuesListSection(
    mapUiState: MapUiState,
    onRetry: () -> Unit,
    onIssueClick: (IssueDto) -> Unit = {}
) {
    when (mapUiState) {
        MapUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        is MapUiState.Error -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = mapUiState.message,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }

        is MapUiState.Success -> {
            if (mapUiState.issues.isEmpty()) {
                Text(
                    text = "No nearby issues available right now.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
                return
            }

            mapUiState.issues.forEachIndexed { index, issue ->
                val (statusContainerColor, statusTextColor) = statusColorsForPriority(issue.priority)
                val status = statusLabelForPriority(issue.priority)
                val modeLabel = when (mapUiState.searchMode) {
                    "location" -> "Nearby via GPS"
                    "district" -> "District-wide"
                    else -> "Nearby"
                }

                val fullIssue = mapUiState.fullIssues.firstOrNull { it.id == issue.id }

                IssueCard(
                    title = issue.title,
                    time = modeLabel,
                    desc = "Community reported issue in your area.",
                    location = issue.addressText ?: "Location unavailable",
                    imageUrl = issue.imageUrl,
                    status = status,
                    statusColor = statusContainerColor,
                    statusTextColor = statusTextColor,
                    voteCount = fullIssue?.voteCount ?: 0,
                    isVotedByMe = fullIssue?.isVotedByMe == true,
                    onClick = { fullIssue?.let { onIssueClick(it) } }
                )

                if (index != mapUiState.issues.lastIndex) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DashboardMapSection(
    mapUiState: MapUiState,
    userDistrictName: String,
    centerPoint: GeoPoint,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // District label at top
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = when (mapUiState) {
                        is MapUiState.Success -> mapUiState.displayInfo
                        else -> "Your District: $userDistrictName"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            when (mapUiState) {
                MapUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is MapUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = mapUiState.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRetry) {
                            Text("Retry")
                        }
                    }
                }

                is MapUiState.Success -> {
                    NearbyIssuesMap(
                        issues = mapUiState.issues,
                        centerPoint = centerPoint,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Community Pulse",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${mapUiState.issues.size} nearby issues mapped",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Mode: ${mapUiState.searchMode.capitalized()}",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyIssuesMap(
    issues: List<MapIssue>,
    centerPoint: GeoPoint,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        OsmConfiguration.getInstance().load(appContext, prefs)
        OsmConfiguration.getInstance().userAgentValue = appContext.packageName

        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
        }
    }
    DisposableEffect(mapView) {
        onDispose {
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .pointerInteropFilter { event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN,
                    android.view.MotionEvent.ACTION_MOVE -> {
                        mapView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        mapView.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            },
        update = { view ->
            view.overlays.clear()

            view.controller.setCenter(centerPoint)

            issues.forEach { issue ->
                val marker = Marker(view).apply {
                    position = GeoPoint(issue.lat, issue.lng)
                    title = issue.title
                    subDescription = issue.addressText ?: "Nearby issue"
                    icon = createMarkerIcon(
                        context = context,
                        fillColor = markerColorForPriority(issue.priority)
                    )
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                view.overlays.add(marker)
            }
            view.invalidate()
        }
    )
}

private fun createMarkerIcon(
    context: android.content.Context,
    fillColor: Int
): BitmapDrawable {
    val size = 96
    val cx = size / 2f
    val headCy = 34f
    val headRadius = 24f
    val tipY = 82f

    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AndroidColor.argb(55, 0, 0, 0)
    }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = AndroidColor.WHITE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
    }
    val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AndroidColor.WHITE
    }
    val centerAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }

    val shadowPath = android.graphics.Path().apply {
        addCircle(cx + 3f, headCy + 4f, headRadius, android.graphics.Path.Direction.CW)
        moveTo(cx - 12f, headCy + 20f)
        lineTo(cx + 3f, tipY + 5f)
        lineTo(cx + 18f, headCy + 20f)
        close()
    }
    canvas.drawPath(shadowPath, shadowPaint)

    val pinPath = android.graphics.Path().apply {
        addCircle(cx, headCy, headRadius, android.graphics.Path.Direction.CW)
        moveTo(cx - 14f, headCy + 18f)
        lineTo(cx, tipY)
        lineTo(cx + 14f, headCy + 18f)
        close()
    }
    canvas.drawPath(pinPath, fillPaint)
    canvas.drawPath(pinPath, strokePaint)
    canvas.drawCircle(cx, headCy, 10f, centerPaint)
    canvas.drawCircle(cx, headCy, 4f, centerAccentPaint)

    return bitmap.toDrawable(context.resources)
}

private fun markerColorForPriority(priority: String?): Int {
    return when (priority?.uppercase()) {
        "HIGH" -> "#D32F2F".toColorInt()
        "LOW" -> "#2E7D32".toColorInt()
        else -> "#F57C00".toColorInt()
    }
}

private fun statusColorsForPriority(priority: String?): Pair<Color, Color> {
    return when (priority?.uppercase()) {
        "HIGH" -> Color(0xFFD32F2F).copy(alpha = 0.1f) to Color(0xFFD32F2F)
        "LOW" -> Color(0xFF2E7D32).copy(alpha = 0.1f) to Color(0xFF2E7D32)
        else -> Color(0xFFF57C00).copy(alpha = 0.1f) to Color(0xFFF57C00)
    }
}

private fun statusLabelForPriority(priority: String?): String {
    return when (priority?.uppercase()) {
        "HIGH" -> "HIGH"
        "LOW" -> "LOW"
        else -> "MEDIUM"
    }
}

private fun IssueDto.toMapIssue(): MapIssue? {
    val issueLat = lat ?: return null
    val issueLng = lng ?: return null

    return MapIssue(
        id = id,
        title = title.ifBlank { "Issue" },
        addressText = addressText,
        lat = issueLat,
        lng = issueLng,
        imageUrl = resolvePreviewImageUrl(),
        priority = priority
    )
}

private fun IssueDto.resolvePreviewImageUrl(): String? {
    // API typically sends images as objects: images[].imageUrl.
    return images.firstNotNullOfOrNull { it.imageUrl?.trim()?.takeIf(String::isNotEmpty) }
        ?: imageUrls?.firstNotNullOfOrNull { it.trim().takeIf(String::isNotEmpty) }
        ?: imageUrl?.trim()?.takeIf(String::isNotEmpty)
}

private sealed interface MapUiState {
    data object Loading : MapUiState
    data class Success(
        val issues: List<MapIssue>,
        val displayInfo: String = "Nearby Issues",
        val searchMode: String = "unknown",
        val fullIssues: List<IssueDto> = emptyList()
    ) : MapUiState
    data class Error(val message: String) : MapUiState
}

private sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val data: DashboardResponse) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

private data class MapIssue(
    val id: String,
    val title: String,
    val addressText: String?,
    val lat: Double,
    val lng: Double,
    val imageUrl: String?,
    val priority: String?
)

private const val DEFAULT_NEARBY_DISTRICT_ID = "20000001-0000-0000-0000-000000000000"

@Composable
fun CategoryChip(label: String, icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun IssueCard(
    title: String,
    time: String,
    desc: String,
    location: String,
    imageUrl: String?,
    status: String,
    statusColor: Color,
    statusTextColor: Color,
    voteCount: Int = 0,
    isVotedByMe: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder for issues without images
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Image Available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                // Status Badge
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor
                ) {
                    Text(
                        status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = statusTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Vote Count Badge
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isVotedByMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (isVotedByMe) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (isVotedByMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = voteCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isVotedByMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    desc,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, size = 14.sp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(location, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// Extension to allow Icon size in sp if needed, but usually dp is preferred. 
// Using a helper for simplicity since Icon doesn't take sp directly easily without local density.
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.TextUnit, tint: Color) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(16.dp), // Fixed size for simplicity
        tint = tint
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DashboardPreview() {
    SevaSetuTheme {
        DashboardScreen()
    }
}

private fun String.capitalized(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
