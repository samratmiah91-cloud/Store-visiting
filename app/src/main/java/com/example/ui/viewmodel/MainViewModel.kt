package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ActivityLogEntity
import com.example.data.local.AppNotificationEntity
import com.example.data.local.AppSettingsEntity
import com.example.data.local.AttendanceEntity
import com.example.data.local.AttendanceStatus
import com.example.data.local.PermissionKey
import com.example.data.local.RequestType
import com.example.data.local.RescheduleRequestEntity
import com.example.data.local.RolePermissionEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.StoreAssignmentEntity
import com.example.data.local.StoreEntity
import com.example.data.local.TaskEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserRole
import com.example.data.local.VisitRecordEntity
import com.example.data.repository.OtpChallenge
import com.example.data.repository.StoreVisitRepository
import com.example.service.BackupManager
import com.example.service.ConnectivityObserver
import com.example.service.LocationService
import com.example.service.LocationState
import com.example.service.PhotoStorageHelper
import com.example.service.ReportExportPayload
import com.example.service.ReportExporter
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StoreWithDistance(
    val store: StoreEntity,
    val assignment: StoreAssignmentEntity,
    val distanceMeters: Double?,
    val isNearestPending: Boolean,
    val markerNumber: Int
)

data class AuthUiState(
    val isLoading: Boolean = false,
    val pendingOtpChallenge: OtpChallenge? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class MainViewModel(
    private val repository: StoreVisitRepository,
    private val prefs: SessionPreferences,
    private val locationService: LocationService,
    private val connectivityObserver: ConnectivityObserver,
    val photoStorageHelper: PhotoStorageHelper,
    private val reportExporter: ReportExporter,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    private val _hasLocationPermission = MutableStateFlow(false)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    val appLanguage: StateFlow<AppLanguage> = prefs.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.ENGLISH)

    val offlineSimulationEnabled: StateFlow<Boolean> = prefs.offlineSimulationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isDeviceOnline: StateFlow<Boolean> = combine(
        connectivityObserver.observeIsOnline(),
        offlineSimulationEnabled
    ) { realOnline, simOffline ->
        realOnline && !simOffline
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val mainSuperAdminCount: StateFlow<Int> = repository.mainSuperAdminCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val currentUser: StateFlow<UserEntity?> = repository.loggedInUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rolePermissions: StateFlow<List<RolePermissionEntity>> = repository.allRolePermissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUserPermissions: StateFlow<RolePermissionEntity> = combine(
        currentUser,
        rolePermissions
    ) { user, allPerms ->
        if (user == null) {
            RolePermissionEntity.defaultsFor(UserRole.VISITOR)
        } else if (user.role == UserRole.MAIN_SUPER_ADMIN) {
            RolePermissionEntity.defaultsFor(UserRole.MAIN_SUPER_ADMIN)
        } else {
            allPerms.firstOrNull { it.roleName == user.role.name }
                ?: RolePermissionEntity.defaultsFor(user.role)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RolePermissionEntity.defaultsFor(UserRole.VISITOR))

    val stores: StateFlow<List<StoreEntity>> = repository.allStores
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val assignments: StateFlow<List<StoreAssignmentEntity>> = repository.allAssignments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visits: StateFlow<List<VisitRecordEntity>> = repository.allVisits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendanceRecords: StateFlow<List<AttendanceEntity>> = repository.allAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rescheduleRequests: StateFlow<List<RescheduleRequestEntity>> = repository.allRescheduleRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<AppNotificationEntity>> = combine(
        currentUser,
        repository.allNotifications
    ) { user, list ->
        if (user == null) emptyList()
        else list.filter { notif ->
            (notif.targetUserId == null && notif.targetRole == null) ||
                notif.targetUserId == user.id ||
                notif.targetRole == user.role.name ||
                user.role == UserRole.MAIN_SUPER_ADMIN
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activityLogs: StateFlow<List<ActivityLogEntity>> = repository.recentActivityLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettings: StateFlow<AppSettingsEntity> = repository.appSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsEntity())

    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val locationState: StateFlow<LocationState> = _hasLocationPermission
        .flatMapLatest { granted -> locationService.observeLocationUpdates(granted) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocationState.Searching)

    val visitorAssignedStoresWithDistance: StateFlow<List<StoreWithDistance>> = combine(
        currentUser,
        assignments,
        stores,
        locationState
    ) { user, allAssignments, allStores, loc ->
        if (user == null) return@combine emptyList()
        val relevant = if (user.role == UserRole.VISITOR) {
            allAssignments.filter { it.visitorId == user.id }
        } else {
            allAssignments
        }.filter { it.status.name != "CANCELLED" }

        val storeMap = allStores.associateBy { it.id }
        val userLatLng = (loc as? LocationState.Available)?.let { it.latitude to it.longitude }

        val mapped = relevant.mapIndexedNotNull { idx, assignment ->
            val store = storeMap[assignment.storeId] ?: return@mapIndexedNotNull null
            val dist = userLatLng?.let { (lat, lng) ->
                LocationService.distanceMeters(lat, lng, store.latitude, store.longitude)
            }
            StoreWithDistance(
                store = store,
                assignment = assignment,
                distanceMeters = dist,
                isNearestPending = false,
                markerNumber = idx + 1
            )
        }

        val nearestPendingId = mapped
            .filter {
                (it.assignment.status.name == "PENDING" || it.assignment.status.name == "LATE" || it.assignment.status.name == "RESCHEDULED") &&
                    it.distanceMeters != null
            }
            .minByOrNull { it.distanceMeters ?: Double.MAX_VALUE }
            ?.assignment?.id

        mapped.map { item ->
            item.copy(isNearestPending = item.assignment.id == nearestPendingId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.initializeDefaults()
            repository.evaluateLateAndMissedVisits()
        }
        // Auto-sync when connectivity returns
        viewModelScope.launch {
            isDeviceOnline.collect { online ->
                if (online) {
                    val synced = repository.syncPendingRecords(currentUser.value)
                    if (synced > 0) {
                        _snackbarMessages.tryEmit("Auto-synced $synced offline record(s).")
                    }
                }
            }
        }
    }

    fun updateLocationPermission(granted: Boolean) {
        _hasLocationPermission.value = granted
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (appLanguage.value == AppLanguage.ENGLISH) {
                AppLanguage.HINDI_BILINGUAL
            } else {
                AppLanguage.ENGLISH
            }
            prefs.setAppLanguage(next)
        }
    }

    fun setOfflineSimulation(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setOfflineSimulation(enabled)
            _snackbarMessages.tryEmit(
                if (enabled) "Offline mode active — new submissions will queue locally."
                else "Online mode restored — checking pending sync queue."
            )
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            if (!isDeviceOnline.value) {
                _snackbarMessages.tryEmit("Cannot sync while offline. Data stays safely queued locally.")
                return@launch
            }
            val count = repository.syncPendingRecords(currentUser.value)
            _snackbarMessages.tryEmit(
                if (count > 0) "Synced $count queued record(s)."
                else "All records are already synced."
            )
        }
    }

    // --- Auth Actions ---

    fun registerInitialMainSuperAdmin(name: String, mobile: String) {
        viewModelScope.launch {
            _authUiState.value = _authUiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            repository.registerInitialMainSuperAdmin(name, mobile)
                .onSuccess { challenge ->
                    _authUiState.value = AuthUiState(
                        isLoading = false,
                        pendingOtpChallenge = challenge,
                        infoMessage = "Main Super Admin created. Enter OTP ${challenge.code} to sign in."
                    )
                }
                .onFailure { err ->
                    _authUiState.value = _authUiState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Registration failed."
                    )
                }
        }
    }

    fun requestOtp(mobile: String) {
        viewModelScope.launch {
            _authUiState.value = _authUiState.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            repository.requestOtpForMobile(mobile)
                .onSuccess { challenge ->
                    _authUiState.value = AuthUiState(
                        isLoading = false,
                        pendingOtpChallenge = challenge,
                        infoMessage = "OTP dispatched for ${challenge.mobileNumber}."
                    )
                }
                .onFailure { err ->
                    _authUiState.value = _authUiState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Unable to send OTP."
                    )
                }
        }
    }

    fun verifyOtp(mobile: String, otp: String) {
        viewModelScope.launch {
            _authUiState.value = _authUiState.value.copy(isLoading = true, errorMessage = null)
            repository.verifyOtpAndLogin(mobile, otp)
                .onSuccess {
                    _authUiState.value = AuthUiState()
                    repository.evaluateLateAndMissedVisits()
                }
                .onFailure { err ->
                    _authUiState.value = _authUiState.value.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Verification failed."
                    )
                }
        }
    }

    fun resetOtpChallenge() {
        _authUiState.value = AuthUiState()
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout(currentUser.value)
            _authUiState.value = AuthUiState()
        }
    }

    // --- Operational Actions ---

    fun saveUser(
        existingId: String?,
        name: String,
        mobile: String,
        role: UserRole,
        isApproved: Boolean,
        isActive: Boolean
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.createOrUpdateUser(actor, existingId, name, mobile, role, isApproved, isActive)
                .onSuccess { _snackbarMessages.tryEmit("User saved successfully.") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Unable to save user.") }
        }
    }

    fun togglePermission(role: UserRole, key: PermissionKey, enabled: Boolean) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateRolePermission(actor, role, key, enabled)
                .onSuccess { _snackbarMessages.tryEmit("${role.displayName}: ${key.label} updated.") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Permission update failed.") }
        }
    }

    fun saveStore(
        existingId: String?,
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
        contactName: String,
        contactPhone: String
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.saveStore(actor, existingId, name, address, latitude, longitude, contactName, contactPhone)
                .onSuccess { _snackbarMessages.tryEmit("Store '$name' saved.") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Unable to save store.") }
        }
    }

    suspend fun fetchFreshLocationOnce(): LocationState {
        return locationService.getFreshLocation(_hasLocationPermission.value)
    }

    fun assignStore(
        storeId: String,
        visitorId: String,
        assignedDate: String,
        scheduledTime: String,
        notes: String
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.assignStoreToVisitor(actor, storeId, visitorId, assignedDate, scheduledTime, notes, isDeviceOnline.value)
                .onSuccess { _snackbarMessages.tryEmit("Store assigned.") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Assignment failed.") }
        }
    }

    fun submitCheckIn(
        assignment: StoreAssignmentEntity,
        store: StoreEntity,
        currentLat: Double,
        currentLng: Double,
        photoUri: String,
        daySiName: String,
        daySiMobile: String,
        nightSiName: String,
        nightSiMobile: String,
        dhmName: String,
        dhmMobile: String,
        clmName: String,
        clmMobile: String,
        onSuccess: () -> Unit
    ) {
        val visitor = currentUser.value ?: return
        viewModelScope.launch {
            repository.performCheckIn(
                visitor = visitor,
                assignment = assignment,
                store = store,
                currentLat = currentLat,
                currentLng = currentLng,
                photoUri = photoUri,
                daySiName = daySiName,
                daySiMobile = daySiMobile,
                nightSiName = nightSiName,
                nightSiMobile = nightSiMobile,
                dhmName = dhmName,
                dhmMobile = dhmMobile,
                clmName = clmName,
                clmMobile = clmMobile,
                isOnline = isDeviceOnline.value
            ).onSuccess {
                _snackbarMessages.tryEmit(
                    if (isDeviceOnline.value) "Checked in at ${store.name}!"
                    else "Checked in offline (queued for sync)."
                )
                onSuccess()
            }.onFailure {
                _snackbarMessages.tryEmit(it.message ?: "Check-in failed.")
            }
        }
    }

    fun submitCheckOut(
        assignment: StoreAssignmentEntity,
        totalGainAmount: Double,
        onSuccess: () -> Unit
    ) {
        val visitor = currentUser.value ?: return
        viewModelScope.launch {
            repository.performCheckOut(visitor, assignment, totalGainAmount, isDeviceOnline.value)
                .onSuccess {
                    _snackbarMessages.tryEmit(
                        if (isDeviceOnline.value) "Visit report submitted automatically!"
                        else "Check-out saved offline (queued for sync)."
                    )
                    onSuccess()
                }
                .onFailure {
                    _snackbarMessages.tryEmit(it.message ?: "Check-out failed.")
                }
        }
    }

    fun createTask(
        storeId: String,
        visitorId: String,
        title: String,
        description: String,
        dueDate: String
    ) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.createTask(actor, storeId, visitorId, title, description, dueDate, isDeviceOnline.value)
                .onSuccess { _snackbarMessages.tryEmit("Task assigned.") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Task creation failed.") }
        }
    }

    fun updateTask(task: TaskEntity, completed: Boolean, remark: String, photoUri: String?) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateTaskCompletion(actor, task, completed, remark, photoUri, isDeviceOnline.value)
                .onSuccess {
                    _snackbarMessages.tryEmit(
                        if (isDeviceOnline.value) "Task updated." else "Task saved offline (queued for sync)."
                    )
                }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Task update failed.") }
        }
    }

    fun markAttendance(visitor: UserEntity, date: String, status: AttendanceStatus, notes: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.markAttendance(actor, visitor, date, status, notes, isDeviceOnline.value)
                .onSuccess { _snackbarMessages.tryEmit("Attendance saved for ${visitor.name}.") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Failed to mark attendance.") }
        }
    }

    fun submitRescheduleRequest(
        assignment: StoreAssignmentEntity,
        store: StoreEntity,
        type: RequestType,
        requestedDate: String?,
        requestedTime: String?,
        reason: String,
        onSuccess: () -> Unit
    ) {
        val visitor = currentUser.value ?: return
        viewModelScope.launch {
            repository.submitRescheduleRequest(visitor, assignment, store, type, requestedDate, requestedTime, reason)
                .onSuccess {
                    _snackbarMessages.tryEmit("Request sent for Supervisor/Manager approval.")
                    onSuccess()
                }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Request failed.") }
        }
    }

    fun reviewRescheduleRequest(request: RescheduleRequestEntity, approved: Boolean, note: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.reviewRescheduleRequest(actor, request, approved, note)
                .onSuccess {
                    _snackbarMessages.tryEmit(
                        if (approved) "Approved and assignment updated." else "Request rejected."
                    )
                }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Review failed.") }
        }
    }

    fun updateSettings(lateMinutes: Int, missedHour: Int, radiusMeters: Int) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.updateSettings(actor, lateMinutes, missedHour, radiusMeters)
                .onSuccess {
                    repository.evaluateLateAndMissedVisits()
                    _snackbarMessages.tryEmit("Application settings updated.")
                }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Settings update failed.") }
        }
    }

    fun sendAnnouncement(title: String, message: String) {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.sendAdminAnnouncement(actor, title, message)
                .onSuccess { _snackbarMessages.tryEmit("Announcement sent to team.") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Announcement failed.") }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { repository.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { repository.markAllNotificationsRead() }
    }

    fun exportExcelCsv(payload: ReportExportPayload) {
        viewModelScope.launch {
            reportExporter.exportExcelCsv(payload)
                .onSuccess { uri ->
                    reportExporter.shareExportUri(uri, "text/csv", "Export Excel (CSV) Report")
                    _snackbarMessages.tryEmit("Excel (CSV) report ready to share/save.")
                }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "CSV export failed.") }
        }
    }

    fun exportPdf(payload: ReportExportPayload) {
        viewModelScope.launch {
            reportExporter.exportPdf(payload)
                .onSuccess { uri ->
                    reportExporter.shareExportUri(uri, "application/pdf", "Export PDF Report")
                    _snackbarMessages.tryEmit("PDF report generated.")
                }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "PDF export failed.") }
        }
    }

    fun createBackup(destUri: Uri) {
        viewModelScope.launch {
            backupManager.exportBackupToUri(destUri)
                .onSuccess { summary -> _snackbarMessages.tryEmit("Backup saved: $summary") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Backup failed.") }
        }
    }

    fun restoreBackup(sourceUri: Uri) {
        viewModelScope.launch {
            backupManager.restoreBackupFromUri(sourceUri)
                .onSuccess { summary -> _snackbarMessages.tryEmit(summary) }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Restore failed.") }
        }
    }

    fun loadSampleOperationsData() {
        val actor = currentUser.value ?: return
        viewModelScope.launch {
            repository.seedSampleOperationsData(actor)
                .onSuccess { _snackbarMessages.tryEmit("Sample stores, team & tasks loaded.") }
                .onFailure { _snackbarMessages.tryEmit(it.message ?: "Failed to load sample data.") }
        }
    }

    fun todayDate(): String = repository.todayString()

    class Factory(private val app: com.example.StoreVisitApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(
                repository = app.repository,
                prefs = app.sessionPreferences,
                locationService = app.locationService,
                connectivityObserver = app.connectivityObserver,
                photoStorageHelper = app.photoStorageHelper,
                reportExporter = app.reportExporter,
                backupManager = app.backupManager
            ) as T
        }
    }
}
