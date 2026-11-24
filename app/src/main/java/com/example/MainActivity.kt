package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.StoreAssignmentEntity
import com.example.data.local.StoreEntity
import com.example.data.local.UserRole
import com.example.ui.components.StoreVisitTopBar
import com.example.ui.i18n.labelsFor
import com.example.ui.screens.AdminControlScreen
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.CheckInSheet
import com.example.ui.screens.CheckOutDialog
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LiveMapScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NotificationsSheet
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.RescheduleRequestDialog
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.StoreVisitTheme
import com.example.ui.viewmodel.MainViewModel

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as StoreVisitApp
        setContent {
            StoreVisitTheme {
                val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(app))
                StoreVisitRoot(vm = vm)
            }
        }
    }
}

@Composable
fun StoreVisitRoot(vm: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val language by vm.appLanguage.collectAsStateWithLifecycle()
    val labels = remember(language) { labelsFor(language) }
    val currentUser by vm.currentUser.collectAsStateWithLifecycle()
    val authUiState by vm.authUiState.collectAsStateWithLifecycle()
    val mainSuperAdminCount by vm.mainSuperAdminCount.collectAsStateWithLifecycle()
    val permissions by vm.currentUserPermissions.collectAsStateWithLifecycle()
    val isOnline by vm.isDeviceOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by vm.pendingSyncCount.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val locationState by vm.locationState.collectAsStateWithLifecycle()
    val allUsers by vm.allUsers.collectAsStateWithLifecycle()
    val rolePermissions by vm.rolePermissions.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val assignments by vm.assignments.collectAsStateWithLifecycle()
    val visits by vm.visits.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val attendance by vm.attendanceRecords.collectAsStateWithLifecycle()
    val rescheduleRequests by vm.rescheduleRequests.collectAsStateWithLifecycle()
    val activityLogs by vm.activityLogs.collectAsStateWithLifecycle()
    val appSettings by vm.appSettings.collectAsStateWithLifecycle()
    val offlineSimulation by vm.offlineSimulationEnabled.collectAsStateWithLifecycle()
    val visitorStoresWithDistance by vm.visitorAssignedStoresWithDistance.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        vm.updateLocationPermission(fine || coarse)
    }

    val requestLocationAndNotifications = remember(permissionLauncher) {
        {
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
            permissionLauncher.launch(perms)
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            vm.updateLocationPermission(true)
        } else {
            requestLocationAndNotifications()
        }
    }

    LaunchedEffect(Unit) {
        vm.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val user = currentUser
    if (user == null) {
        LoginScreen(
            authUiState = authUiState,
            mainSuperAdminCount = mainSuperAdminCount,
            language = language,
            labels = labels,
            onToggleLanguage = vm::toggleLanguage,
            onRegisterInitialAdmin = vm::registerInitialMainSuperAdmin,
            onRequestOtp = vm::requestOtp,
            onVerifyOtp = vm::verifyOtp,
            onResetOtp = vm::resetOtpChallenge
        )
        return
    }

    var currentTab by rememberSaveable(user.id) { mutableStateOf("home") }
    var showNotificationsSheet by rememberSaveable { mutableStateOf(false) }
    var checkInTarget by remember { mutableStateOf<Pair<StoreEntity, StoreAssignmentEntity>?>(null) }
    var checkOutTarget by remember { mutableStateOf<Pair<StoreEntity, StoreAssignmentEntity>?>(null) }
    var rescheduleTarget by remember { mutableStateOf<Pair<StoreEntity, StoreAssignmentEntity>?>(null) }

    val navDestinations = remember(user.role, permissions, labels) {
        buildList {
            add(NavDestination("home", labels.dashboard, Icons.Filled.Home))
            if (permissions.canViewLiveMap || user.role == UserRole.MAIN_SUPER_ADMIN) {
                add(NavDestination("map", labels.liveMap, Icons.Filled.Map))
            }
            add(NavDestination("tasks", labels.tasks, Icons.Filled.TaskAlt))
            add(NavDestination("attendance", labels.attendance, Icons.Filled.EventAvailable))
            if (permissions.canViewReports || user.role == UserRole.MAIN_SUPER_ADMIN) {
                add(NavDestination("reports", labels.reports, Icons.Filled.Assessment))
            }
            if (
                user.role == UserRole.MAIN_SUPER_ADMIN ||
                permissions.canManageUsers ||
                permissions.canManageStores ||
                permissions.canAssignStores ||
                permissions.canApproveReschedule ||
                permissions.canSendAnnouncements ||
                permissions.canConfigureSettings
            ) {
                add(NavDestination("admin", labels.governance, Icons.Filled.AdminPanelSettings))
            }
        }
    }

    val activeTab = if (navDestinations.any { it.route == currentTab }) currentTab else "home"

    if (activeTab != "home") {
        BackHandler { currentTab = "home" }
    }

    val unreadCount = notifications.count { !it.isRead }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                StoreVisitTopBar(
                    user = user,
                    isOnline = isOnline,
                    pendingSyncCount = pendingSyncCount,
                    unreadNotifications = unreadCount,
                    language = language,
                    labels = labels,
                    onSyncClick = vm::syncNow,
                    onToggleLanguage = vm::toggleLanguage,
                    onOpenNotifications = { showNotificationsSheet = true },
                    onLogout = vm::logout
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        navDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = activeTab == dest.route,
                                onClick = { currentTab = dest.route },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        navDestinations.forEach { dest ->
                            NavigationRailItem(
                                selected = activeTab == dest.route,
                                onClick = { currentTab = dest.route },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                        when (activeTab) {
                            "home" -> HomeDashboardScreen(
                                user = user,
                                permissions = permissions,
                                users = allUsers,
                                stores = stores,
                                assignments = assignments,
                                visits = visits,
                                tasks = tasks,
                                attendance = attendance,
                                rescheduleRequests = rescheduleRequests,
                                visitorStoresWithDistance = visitorStoresWithDistance,
                                labels = labels,
                                todayDate = vm.todayDate(),
                                onNavigateTab = { currentTab = it },
                                onCheckInClick = { s, a -> checkInTarget = s to a },
                                onCheckOutClick = { s, a -> checkOutTarget = s to a },
                                onLoadSampleData = vm::loadSampleOperationsData
                            )
                            "map" -> LiveMapScreen(
                                storesWithDistance = visitorStoresWithDistance,
                                locationState = locationState,
                                permissions = permissions,
                                settings = appSettings,
                                labels = labels,
                                onRequestLocationPermission = requestLocationAndNotifications,
                                onCheckInClick = { s, a -> checkInTarget = s to a },
                                onCheckOutClick = { s, a -> checkOutTarget = s to a },
                                onRescheduleClick = { s, a -> rescheduleTarget = s to a }
                            )
                            "tasks" -> TasksScreen(
                                user = user,
                                permissions = permissions,
                                tasks = tasks,
                                stores = stores,
                                users = allUsers,
                                todayDate = vm.todayDate(),
                                labels = labels,
                                photoStorageHelper = vm.photoStorageHelper,
                                onCreateTask = vm::createTask,
                                onUpdateTask = vm::updateTask
                            )
                            "attendance" -> AttendanceScreen(
                                user = user,
                                permissions = permissions,
                                users = allUsers,
                                attendanceRecords = attendance,
                                todayDate = vm.todayDate(),
                                labels = labels,
                                onMarkAttendance = vm::markAttendance
                            )
                            "reports" -> ReportsScreen(
                                user = user,
                                permissions = permissions,
                                users = allUsers,
                                stores = stores,
                                assignments = assignments,
                                visits = visits,
                                tasks = tasks,
                                attendance = attendance,
                                todayDate = vm.todayDate(),
                                labels = labels,
                                onExportExcelCsv = vm::exportExcelCsv,
                                onExportPdf = vm::exportPdf
                            )
                            "admin" -> AdminControlScreen(
                                currentUser = user,
                                permissions = permissions,
                                users = allUsers,
                                rolePermissions = rolePermissions,
                                stores = stores,
                                rescheduleRequests = rescheduleRequests,
                                activityLogs = activityLogs,
                                appSettings = appSettings,
                                offlineSimulationEnabled = offlineSimulation,
                                todayDate = vm.todayDate(),
                                onSaveUser = vm::saveUser,
                                onTogglePermission = vm::togglePermission,
                                onSaveStore = vm::saveStore,
                                onFetchFreshLocation = vm::fetchFreshLocationOnce,
                                onAssignStore = vm::assignStore,
                                onReviewReschedule = vm::reviewRescheduleRequest,
                                onUpdateSettings = vm::updateSettings,
                                onSendAnnouncement = vm::sendAnnouncement,
                                onToggleOfflineSim = vm::setOfflineSimulation,
                                onCreateBackup = vm::createBackup,
                                onRestoreBackup = vm::restoreBackup
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNotificationsSheet) {
        NotificationsSheet(
            notifications = notifications,
            onMarkRead = vm::markNotificationRead,
            onMarkAllRead = vm::markAllNotificationsRead,
            onDismiss = { showNotificationsSheet = false }
        )
    }

    checkInTarget?.let { (store, assignment) ->
        CheckInSheet(
            store = store,
            assignment = assignment,
            visitor = user,
            locationState = locationState,
            settings = appSettings,
            labels = labels,
            photoStorageHelper = vm.photoStorageHelper,
            onRequestLocationPermission = requestLocationAndNotifications,
            onSubmitCheckIn = { lat, lng, photoUri, dSiN, dSiM, nSiN, nSiM, dhmN, dhmM, clmN, clmM ->
                vm.submitCheckIn(
                    assignment = assignment,
                    store = store,
                    currentLat = lat,
                    currentLng = lng,
                    photoUri = photoUri,
                    daySiName = dSiN,
                    daySiMobile = dSiM,
                    nightSiName = nSiN,
                    nightSiMobile = nSiM,
                    dhmName = dhmN,
                    dhmMobile = dhmM,
                    clmName = clmN,
                    clmMobile = clmM,
                    onSuccess = { checkInTarget = null }
                )
            },
            onDismiss = { checkInTarget = null }
        )
    }

    checkOutTarget?.let { (store, assignment) ->
        CheckOutDialog(
            storeName = store.name,
            visitorName = user.name,
            labels = labels,
            onConfirmCheckOut = { gain ->
                vm.submitCheckOut(assignment, gain) {
                    checkOutTarget = null
                }
            },
            onDismiss = { checkOutTarget = null }
        )
    }

    rescheduleTarget?.let { (store, assignment) ->
        RescheduleRequestDialog(
            storeName = store.name,
            defaultDate = assignment.assignedDate,
            defaultTime = assignment.scheduledTime,
            onSubmit = { type, date, time, reason ->
                vm.submitRescheduleRequest(assignment, store, type, date, time, reason) {
                    rescheduleTarget = null
                }
            },
            onDismiss = { rescheduleTarget = null }
        )
    }
}
