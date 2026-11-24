package com.example.data.repository

import com.example.data.local.ActivityLogEntity
import com.example.data.local.AppDatabase
import com.example.data.local.AppNotificationEntity
import com.example.data.local.AppSettingsEntity
import com.example.data.local.ApprovalStatus
import com.example.data.local.AssignmentStatus
import com.example.data.local.AttendanceEntity
import com.example.data.local.AttendanceStatus
import com.example.data.local.NotificationType
import com.example.data.local.PermissionKey
import com.example.data.local.RequestType
import com.example.data.local.RescheduleRequestEntity
import com.example.data.local.RolePermissionEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.StoreAssignmentEntity
import com.example.data.local.StoreEntity
import com.example.data.local.SyncStatus
import com.example.data.local.TaskEntity
import com.example.data.local.TaskStatus
import com.example.data.local.UserEntity
import com.example.data.local.UserRole
import com.example.data.local.VisitRecordEntity
import com.example.service.LocationService
import com.example.service.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class OtpChallenge(
    val mobileNumber: String,
    val code: String,
    val expiresAtMillis: Long,
    val attemptsLeft: Int = 5
)

class StoreVisitRepository(
    private val db: AppDatabase,
    private val prefs: SessionPreferences,
    private val notificationHelper: NotificationHelper
) {
    private val userDao = db.userDao()
    private val permissionDao = db.rolePermissionDao()
    private val storeDao = db.storeDao()
    private val assignmentDao = db.storeAssignmentDao()
    private val visitDao = db.visitRecordDao()
    private val taskDao = db.taskDao()
    private val attendanceDao = db.attendanceDao()
    private val rescheduleDao = db.rescheduleRequestDao()
    private val notificationDao = db.notificationDao()
    private val activityLogDao = db.activityLogDao()
    private val settingsDao = db.appSettingsDao()

    private val random = SecureRandom()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.US)

    private var activeOtpChallenge: OtpChallenge? = null

    val allUsers: Flow<List<UserEntity>> = userDao.observeAllUsers()
    val allRolePermissions: Flow<List<RolePermissionEntity>> = permissionDao.observeAllRolePermissions()
    val allStores: Flow<List<StoreEntity>> = storeDao.observeAllStores()
    val allAssignments: Flow<List<StoreAssignmentEntity>> = assignmentDao.observeAllAssignments()
    val allVisits: Flow<List<VisitRecordEntity>> = visitDao.observeAllVisits()
    val allTasks: Flow<List<TaskEntity>> = taskDao.observeAllTasks()
    val allAttendance: Flow<List<AttendanceEntity>> = attendanceDao.observeAllAttendance()
    val allRescheduleRequests: Flow<List<RescheduleRequestEntity>> = rescheduleDao.observeAllRequests()
    val allNotifications: Flow<List<AppNotificationEntity>> = notificationDao.observeAllNotifications()
    val recentActivityLogs: Flow<List<ActivityLogEntity>> = activityLogDao.observeRecentLogs()
    val appSettings: Flow<AppSettingsEntity> = settingsDao.observeSettings().map { it ?: AppSettingsEntity() }
    val mainSuperAdminCount: Flow<Int> = userDao.observeMainSuperAdminCount()

    val loggedInUser: Flow<UserEntity?> = combine(
        prefs.loggedInUserId,
        allUsers
    ) { userId, users ->
        users.firstOrNull { it.id == userId && it.isApproved && it.isActive }
    }

    val pendingSyncCount: Flow<Int> = combine(
        allAssignments,
        allVisits,
        allTasks,
        allAttendance
    ) { assignments, visits, tasks, attendance ->
        assignments.count { it.syncStatus == SyncStatus.PENDING_SYNC } +
            visits.count { it.syncStatus == SyncStatus.PENDING_SYNC } +
            tasks.count { it.syncStatus == SyncStatus.PENDING_SYNC } +
            attendance.count { it.syncStatus == SyncStatus.PENDING_SYNC }
    }

    suspend fun initializeDefaults() {
        AppDatabase.ensureDefaults(db)
    }

    fun todayString(): String = dateFormatter.format(Date())

    // --- Authentication (Mobile + OTP, Registered & Approved Only) ---

    suspend fun registerInitialMainSuperAdmin(name: String, mobileNumber: String): Result<OtpChallenge> = runCatching {
        val cleanMobile = normalizeMobile(mobileNumber)
        require(name.trim().length >= 2) { "Please enter your full name." }
        require(cleanMobile.length == 10) { "Enter a valid 10-digit mobile number." }
        require(userDao.getMainSuperAdminCount() == 0) { "Main Super Admin is already registered." }

        val adminUser = UserEntity(
            id = "user_msa_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            mobileNumber = cleanMobile,
            role = UserRole.MAIN_SUPER_ADMIN,
            isApproved = true,
            isActive = true
        )
        AppDatabase.ensureDefaults(db)
        userDao.upsertUser(adminUser)
        logAction(adminUser, "REGISTER_MAIN_SUPER_ADMIN", "Initialized Main Super Admin account ($cleanMobile)")
        requestOtpForMobile(cleanMobile).getOrThrow()
    }

    suspend fun requestOtpForMobile(rawMobile: String): Result<OtpChallenge> = runCatching {
        val mobile = normalizeMobile(rawMobile)
        require(mobile.length == 10) { "Enter a valid 10-digit mobile number." }

        val user = userDao.getUserByMobile(mobile)
            ?: error("Mobile number $mobile is not registered. Ask Main Super Admin to create your account.")
        require(user.isApproved) { "Your account is pending Main Super Admin approval." }
        require(user.isActive) { "Your account is deactivated. Contact Main Super Admin." }

        val otp = (100000 + random.nextInt(900000)).toString()
        val challenge = OtpChallenge(
            mobileNumber = mobile,
            code = otp,
            expiresAtMillis = System.currentTimeMillis() + 5 * 60 * 1000L,
            attemptsLeft = 5
        )
        activeOtpChallenge = challenge
        notificationHelper.showSystemNotification(
            title = "StoreVisit Login OTP",
            body = "Your verification code for $mobile is $otp. Valid for 5 minutes."
        )
        challenge
    }

    suspend fun verifyOtpAndLogin(rawMobile: String, enteredOtp: String): Result<UserEntity> = runCatching {
        val mobile = normalizeMobile(rawMobile)
        val challenge = activeOtpChallenge ?: error("Please request an OTP first.")
        require(challenge.mobileNumber == mobile) { "OTP was requested for a different mobile number." }
        require(System.currentTimeMillis() <= challenge.expiresAtMillis) { "OTP expired. Request a new code." }
        require(challenge.attemptsLeft > 0) { "Too many failed attempts. Request a new OTP." }

        if (enteredOtp.trim() != challenge.code) {
            activeOtpChallenge = challenge.copy(attemptsLeft = challenge.attemptsLeft - 1)
            error("Incorrect OTP. ${challenge.attemptsLeft - 1} attempts remaining.")
        }

        val user = userDao.getUserByMobile(mobile)
            ?: error("Account not found.")
        require(user.isApproved && user.isActive) { "Account not approved or deactivated." }

        activeOtpChallenge = null
        prefs.setLoggedInUserId(user.id)
        logAction(user, "LOGIN", "Logged in via Mobile OTP (${user.role.displayName})")
        user
    }

    suspend fun logout(actor: UserEntity?) {
        if (actor != null) {
            logAction(actor, "LOGOUT", "Signed out")
        }
        prefs.setLoggedInUserId(null)
    }

    // --- User & Permission Governance ---

    suspend fun createOrUpdateUser(
        actor: UserEntity,
        existingId: String?,
        name: String,
        mobileNumber: String,
        role: UserRole,
        isApproved: Boolean,
        isActive: Boolean
    ): Result<Unit> = runCatching {
        requirePermission(actor, PermissionKey.MANAGE_USERS)
        val cleanMobile = normalizeMobile(mobileNumber)
        require(name.trim().length >= 2) { "Name must be at least 2 characters." }
        require(cleanMobile.length == 10) { "Mobile number must be 10 digits." }

        if (actor.role != UserRole.MAIN_SUPER_ADMIN) {
            require(role != UserRole.MAIN_SUPER_ADMIN) {
                "Only Main Super Admin can assign Main Super Admin role."
            }
            require(role.hierarchyRank < actor.role.hierarchyRank || role == actor.role) {
                "Cannot create or elevate a user above your own role."
            }
        }

        val duplicate = userDao.getUserByMobile(cleanMobile)
        require(duplicate == null || duplicate.id == existingId) {
            "A user with mobile $cleanMobile already exists."
        }

        val existing = existingId?.let { userDao.getUserById(it) }
        if (existing != null && existing.role == UserRole.MAIN_SUPER_ADMIN && actor.role != UserRole.MAIN_SUPER_ADMIN) {
            error("Only Main Super Admin can modify Main Super Admin accounts.")
        }

        val entity = existing?.copy(
            name = name.trim(),
            mobileNumber = cleanMobile,
            role = role,
            isApproved = isApproved,
            isActive = isActive
        ) ?: UserEntity(
            id = "usr_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            mobileNumber = cleanMobile,
            role = role,
            isApproved = isApproved,
            isActive = isActive
        )
        userDao.upsertUser(entity)
        logAction(actor, if (existing == null) "CREATE_USER" else "UPDATE_USER", "${entity.name} (${entity.role.displayName}, $cleanMobile)")
    }

    suspend fun updateRolePermission(
        actor: UserEntity,
        targetRole: UserRole,
        key: PermissionKey,
        enabled: Boolean
    ): Result<Unit> = runCatching {
        require(actor.role == UserRole.MAIN_SUPER_ADMIN) {
            "Only Main Super Admin can configure role permissions."
        }
        require(targetRole != UserRole.MAIN_SUPER_ADMIN) {
            "Main Super Admin always retains full permissions."
        }
        val current = permissionDao.getPermissionForRole(targetRole.name)
            ?: RolePermissionEntity.defaultsFor(targetRole)
        val updated = current.withPermission(key, enabled)
        permissionDao.upsertPermission(updated)
        logAction(actor, "UPDATE_PERMISSION", "${targetRole.displayName}: ${key.label} -> $enabled")
    }

    // --- Store Management & Unlimited Daily Assignments ---

    suspend fun saveStore(
        actor: UserEntity,
        existingId: String?,
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
        contactName: String,
        contactPhone: String
    ): Result<Unit> = runCatching {
        requirePermission(actor, PermissionKey.MANAGE_STORES)
        require(name.trim().isNotEmpty()) { "Store name is required." }
        require(address.trim().isNotEmpty()) { "Store address is required." }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "Enter valid GPS coordinates." }

        val existing = existingId?.let { storeDao.getStoreById(it) }
        val store = existing?.copy(
            name = name.trim(),
            address = address.trim(),
            latitude = latitude,
            longitude = longitude,
            contactName = contactName.trim(),
            contactPhone = contactPhone.trim()
        ) ?: StoreEntity(
            id = "store_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            address = address.trim(),
            latitude = latitude,
            longitude = longitude,
            contactName = contactName.trim(),
            contactPhone = contactPhone.trim()
        )
        storeDao.upsertStore(store)
        logAction(actor, if (existing == null) "ADD_STORE" else "EDIT_STORE", "${store.name} (${store.latitude}, ${store.longitude})")
    }

    suspend fun assignStoreToVisitor(
        actor: UserEntity,
        storeId: String,
        visitorId: String,
        assignedDate: String,
        scheduledTime: String,
        notes: String,
        isOnline: Boolean
    ): Result<Unit> = runCatching {
        requirePermission(actor, PermissionKey.ASSIGN_STORES)
        val store = storeDao.getStoreById(storeId) ?: error("Selected store not found.")
        val visitor = userDao.getUserById(visitorId) ?: error("Selected visitor not found.")

        val existingForVisitorDay = assignmentDao.observeAssignmentsForVisitor(visitorId).first()
            .filter { it.assignedDate == assignedDate }
        val nextOrder = (existingForVisitorDay.maxOfOrNull { it.sequenceOrder } ?: 0) + 1

        val assignment = StoreAssignmentEntity(
            id = "asgn_${UUID.randomUUID().toString().take(8)}",
            storeId = store.id,
            visitorId = visitor.id,
            assignedDate = assignedDate,
            scheduledTime = scheduledTime,
            status = AssignmentStatus.PENDING,
            sequenceOrder = nextOrder,
            assignedByUserId = actor.id,
            notes = notes.trim(),
            syncStatus = if (isOnline) SyncStatus.SYNCED else SyncStatus.PENDING_SYNC
        )
        assignmentDao.upsertAssignment(assignment)

        sendNotification(
            targetUserId = visitor.id,
            type = NotificationType.ASSIGNMENT,
            title = "New Store Assigned: ${store.name}",
            message = "Assigned for $assignedDate at $scheduledTime. Address: ${store.address}"
        )
        logAction(actor, "ASSIGN_STORE", "Assigned ${store.name} to ${visitor.name} on $assignedDate")
    }

    // --- Check-In & Check-Out & Auto-Saved SI/DHM/CLM ---

    suspend fun performCheckIn(
        visitor: UserEntity,
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
        isOnline: Boolean
    ): Result<VisitRecordEntity> = runCatching {
        requirePermission(visitor, PermissionKey.CHECK_IN_OUT)
        require(assignment.status == AssignmentStatus.PENDING || assignment.status == AssignmentStatus.LATE || assignment.status == AssignmentStatus.RESCHEDULED) {
            "This store visit is already ${assignment.status.label}."
        }
        require(photoUri.isNotBlank()) { "Check-in photo is mandatory." }

        val settings = settingsDao.getSettings() ?: AppSettingsEntity()
        val distanceMeters = LocationService.distanceMeters(
            currentLat, currentLng, store.latitude, store.longitude
        )
        require(distanceMeters <= settings.checkInRadiusMeters) {
            "Check-in blocked: You are ${distanceMeters.toInt()} m away. Must be within ${settings.checkInRadiusMeters} m of ${store.name}."
        }

        require(daySiName.isNotBlank() && daySiMobile.isNotBlank()) { "Day SI name and mobile are required." }
        require(nightSiName.isNotBlank() && nightSiMobile.isNotBlank()) { "Night SI name and mobile are required." }
        require(dhmName.isNotBlank() && dhmMobile.isNotBlank()) { "DHM name and mobile are required." }
        require(clmName.isNotBlank() && clmMobile.isNotBlank()) { "CLM name and mobile are required." }

        val submissionKey = "checkin_${assignment.id}"
        val existingVisit = visitDao.getVisitBySubmissionKey(submissionKey)
        if (existingVisit != null) {
            return@runCatching existingVisit
        }

        val now = System.currentTimeMillis()
        val isLate = isNowPastThreshold(assignment.assignedDate, assignment.scheduledTime, settings.lateThresholdMinutes, now)

        val visit = VisitRecordEntity(
            id = "vis_${UUID.randomUUID().toString().take(8)}",
            assignmentId = assignment.id,
            storeId = store.id,
            storeName = store.name,
            visitorId = visitor.id,
            visitorName = visitor.name,
            visitDate = assignment.assignedDate,
            checkInTime = now,
            checkInLat = currentLat,
            checkInLng = currentLng,
            distanceMetersAtCheckIn = distanceMeters,
            checkInPhotoUri = photoUri,
            daySiName = daySiName.trim(),
            daySiMobile = daySiMobile.trim(),
            nightSiName = nightSiName.trim(),
            nightSiMobile = nightSiMobile.trim(),
            dhmName = dhmName.trim(),
            dhmMobile = dhmMobile.trim(),
            clmName = clmName.trim(),
            clmMobile = clmMobile.trim(),
            isLate = isLate,
            syncStatus = if (isOnline) SyncStatus.SYNCED else SyncStatus.PENDING_SYNC,
            submissionKey = submissionKey
        )
        visitDao.upsertVisit(visit)

        // Auto-save SI / DHM / CLM defaults for future visits
        userDao.upsertUser(
            visitor.copy(
                daySiName = daySiName.trim(),
                daySiMobile = daySiMobile.trim(),
                nightSiName = nightSiName.trim(),
                nightSiMobile = nightSiMobile.trim(),
                dhmName = dhmName.trim(),
                dhmMobile = dhmMobile.trim(),
                clmName = clmName.trim(),
                clmMobile = clmMobile.trim()
            )
        )

        assignmentDao.upsertAssignment(
            assignment.copy(
                status = AssignmentStatus.CHECKED_IN,
                syncStatus = if (isOnline) SyncStatus.SYNCED else SyncStatus.PENDING_SYNC
            )
        )

        if (isLate) {
            sendNotification(
                targetRole = UserRole.SUPERVISOR.name,
                type = NotificationType.LATE_MISSED,
                title = "Late Check-In Alert",
                message = "${visitor.name} checked in late at ${store.name} (${assignment.assignedDate} ${assignment.scheduledTime})."
            )
        }

        logAction(visitor, "CHECK_IN", "Checked in at ${store.name} (${distanceMeters.toInt()} m)")
        visit
    }

    suspend fun performCheckOut(
        visitor: UserEntity,
        assignment: StoreAssignmentEntity,
        totalGainAmount: Double,
        isOnline: Boolean
    ): Result<VisitRecordEntity> = runCatching {
        requirePermission(visitor, PermissionKey.CHECK_IN_OUT)
        require(assignment.status == AssignmentStatus.CHECKED_IN) { "Must check in before checking out." }
        require(totalGainAmount >= 0.0) { "Total Gain Amount cannot be negative." }

        val existingVisit = visitDao.getVisitByAssignmentId(assignment.id)
            ?: error("Check-in record missing for this store visit.")
        if (existingVisit.reportSubmitted) {
            return@runCatching existingVisit
        }

        val now = System.currentTimeMillis()
        val updatedVisit = existingVisit.copy(
            checkOutTime = now,
            totalGainAmount = totalGainAmount,
            reportSubmitted = true,
            reportSubmittedAt = now,
            syncStatus = if (isOnline) SyncStatus.SYNCED else SyncStatus.PENDING_SYNC
        )
        visitDao.upsertVisit(updatedVisit)
        assignmentDao.upsertAssignment(
            assignment.copy(
                status = AssignmentStatus.COMPLETED,
                syncStatus = if (isOnline) SyncStatus.SYNCED else SyncStatus.PENDING_SYNC
            )
        )
        logAction(
            visitor,
            "CHECK_OUT_REPORT",
            "Checked out from ${existingVisit.storeName} — Gain ₹${String.format(Locale.US, "%.2f", totalGainAmount)} (Report auto-submitted)"
        )
        updatedVisit
    }

    // --- Task Management ---

    suspend fun createTask(
        actor: UserEntity,
        storeId: String,
        visitorId: String,
        title: String,
        description: String,
        dueDate: String,
        isOnline: Boolean
    ): Result<Unit> = runCatching {
        requirePermission(actor, PermissionKey.CREATE_TASKS)
        require(title.trim().isNotEmpty()) { "Task title is required." }
        val store = storeDao.getStoreById(storeId) ?: error("Store not found.")
        val visitor = userDao.getUserById(visitorId) ?: error("Visitor not found.")

        val task = TaskEntity(
            id = "tsk_${UUID.randomUUID().toString().take(8)}",
            storeId = store.id,
            storeName = store.name,
            visitorId = visitor.id,
            visitorName = visitor.name,
            createdByUserId = actor.id,
            createdByName = actor.name,
            title = title.trim(),
            description = description.trim(),
            status = TaskStatus.PENDING,
            dueDate = dueDate,
            syncStatus = if (isOnline) SyncStatus.SYNCED else SyncStatus.PENDING_SYNC
        )
        taskDao.upsertTask(task)
        sendNotification(
            targetUserId = visitor.id,
            type = NotificationType.TASK,
            title = "New Task: ${task.title}",
            message = "Store: ${store.name} • Due: $dueDate"
        )
        logAction(actor, "CREATE_TASK", "Assigned '${task.title}' to ${visitor.name} at ${store.name}")
    }

    suspend fun updateTaskCompletion(
        actor: UserEntity,
        task: TaskEntity,
        completed: Boolean,
        remark: String,
        photoUri: String?,
        isOnline: Boolean
    ): Result<Unit> = runCatching {
        val updated = task.copy(
            status = if (completed) TaskStatus.COMPLETED else TaskStatus.PENDING,
            remark = remark.trim(),
            photoUri = photoUri ?: task.photoUri,
            completedAt = if (completed) System.currentTimeMillis() else null,
            syncStatus = if (isOnline) SyncStatus.SYNCED else SyncStatus.PENDING_SYNC
        )
        taskDao.upsertTask(updated)
        logAction(actor, "UPDATE_TASK", "Marked '${task.title}' as ${updated.status.label}")
    }

    // --- Separate Attendance ---

    suspend fun markAttendance(
        actor: UserEntity,
        visitor: UserEntity,
        date: String,
        status: AttendanceStatus,
        notes: String,
        isOnline: Boolean
    ): Result<Unit> = runCatching {
        requirePermission(actor, PermissionKey.MARK_ATTENDANCE)
        val existing = attendanceDao.getAttendanceForVisitorOnDate(visitor.id, date)
        val record = AttendanceEntity(
            id = existing?.id ?: "att_${visitor.id}_$date",
            visitorId = visitor.id,
            visitorName = visitor.name,
            date = date,
            status = status,
            markedByUserId = actor.id,
            markedByName = actor.name,
            notes = notes.trim(),
            markedAt = System.currentTimeMillis(),
            syncStatus = if (isOnline) SyncStatus.SYNCED else SyncStatus.PENDING_SYNC
        )
        attendanceDao.upsertAttendance(record)
        logAction(actor, "MARK_ATTENDANCE", "${visitor.name} marked ${status.label} for $date")
    }

    // --- Cancel / Reschedule Requests ---

    suspend fun submitRescheduleRequest(
        visitor: UserEntity,
        assignment: StoreAssignmentEntity,
        store: StoreEntity,
        requestType: RequestType,
        requestedDate: String?,
        requestedTime: String?,
        reason: String
    ): Result<Unit> = runCatching {
        require(reason.trim().length >= 3) { "Please provide a clear reason." }
        if (requestType == RequestType.RESCHEDULE) {
            require(!requestedDate.isNullOrBlank() && !requestedTime.isNullOrBlank()) {
                "Select new date and time for rescheduling."
            }
        }
        val req = RescheduleRequestEntity(
            id = "req_${UUID.randomUUID().toString().take(8)}",
            assignmentId = assignment.id,
            storeId = store.id,
            storeName = store.name,
            visitorId = visitor.id,
            visitorName = visitor.name,
            requestType = requestType,
            requestedDate = requestedDate,
            requestedTime = requestedTime,
            reason = reason.trim()
        )
        rescheduleDao.upsertRequest(req)
        sendNotification(
            targetRole = UserRole.SUPERVISOR.name,
            type = NotificationType.RESCHEDULE,
            title = "${requestType.label} Request",
            message = "${visitor.name} requested ${requestType.label.lowercase()} for ${store.name}: ${req.reason}"
        )
        logAction(visitor, "REQUEST_${requestType.name}", "${store.name}: ${req.reason}")
    }

    suspend fun reviewRescheduleRequest(
        actor: UserEntity,
        request: RescheduleRequestEntity,
        approved: Boolean,
        reviewNote: String
    ): Result<Unit> = runCatching {
        requirePermission(actor, PermissionKey.APPROVE_RESCHEDULE)
        require(request.status == ApprovalStatus.PENDING) { "Request already reviewed." }

        val updatedReq = request.copy(
            status = if (approved) ApprovalStatus.APPROVED else ApprovalStatus.REJECTED,
            reviewedByUserId = actor.id,
            reviewedByName = actor.name,
            reviewNote = reviewNote.trim(),
            reviewedAt = System.currentTimeMillis()
        )
        rescheduleDao.upsertRequest(updatedReq)

        // Update assignment ONLY after approval
        if (approved) {
            val assignment = assignmentDao.getAssignmentById(request.assignmentId)
            if (assignment != null) {
                val updatedAssignment = when (request.requestType) {
                    RequestType.CANCEL -> assignment.copy(status = AssignmentStatus.CANCELLED)
                    RequestType.RESCHEDULE -> assignment.copy(
                        assignedDate = request.requestedDate ?: assignment.assignedDate,
                        scheduledTime = request.requestedTime ?: assignment.scheduledTime,
                        status = AssignmentStatus.RESCHEDULED
                    )
                }
                assignmentDao.upsertAssignment(updatedAssignment)
            }
        }

        sendNotification(
            targetUserId = request.visitorId,
            type = NotificationType.RESCHEDULE,
            title = "${request.requestType.label} ${if (approved) "Approved" else "Rejected"}",
            message = "${request.storeName}: ${if (approved) "Updated schedule applied." else "Request declined."} ${reviewNote.trim()}"
        )
        logAction(actor, "REVIEW_RESCHEDULE", "${request.storeName} -> ${updatedReq.status.label}")
    }

    // --- Settings, Late/Missed Evaluation, Announcements & Offline Sync ---

    suspend fun evaluateLateAndMissedVisits() {
        val settings = settingsDao.getSettings() ?: AppSettingsEntity()
        val now = System.currentTimeMillis()
        val today = todayString()
        val currentCal = Calendar.getInstance().apply { timeInMillis = now }
        val currentHour = currentCal.get(Calendar.HOUR_OF_DAY)

        val assignments = assignmentDao.observeAllAssignments().first()
        assignments.filter { it.status == AssignmentStatus.PENDING || it.status == AssignmentStatus.RESCHEDULED }
            .forEach { assignment ->
                val isPastDay = assignment.assignedDate < today
                val isMissedToday = assignment.assignedDate == today && currentHour >= settings.missedCutoffHour
                if (isPastDay || isMissedToday) {
                    assignmentDao.upsertAssignment(assignment.copy(status = AssignmentStatus.MISSED))
                    val store = storeDao.getStoreById(assignment.storeId)
                    val visitor = userDao.getUserById(assignment.visitorId)
                    if (store != null && visitor != null) {
                        val existingVisit = visitDao.getVisitByAssignmentId(assignment.id)
                        if (existingVisit == null) {
                            visitDao.upsertVisit(
                                VisitRecordEntity(
                                    id = "vis_miss_${assignment.id}",
                                    assignmentId = assignment.id,
                                    storeId = store.id,
                                    storeName = store.name,
                                    visitorId = visitor.id,
                                    visitorName = visitor.name,
                                    visitDate = assignment.assignedDate,
                                    isMissed = true,
                                    submissionKey = "missed_${assignment.id}"
                                )
                            )
                        }
                        sendNotification(
                            targetUserId = visitor.id,
                            type = NotificationType.LATE_MISSED,
                            title = "Missed Visit Recorded",
                            message = "${store.name} scheduled for ${assignment.assignedDate} ${assignment.scheduledTime} was marked Missed."
                        )
                    }
                } else if (assignment.assignedDate == today && isNowPastThreshold(assignment.assignedDate, assignment.scheduledTime, settings.lateThresholdMinutes, now)) {
                    assignmentDao.upsertAssignment(assignment.copy(status = AssignmentStatus.LATE))
                }
            }
    }

    suspend fun updateSettings(
        actor: UserEntity,
        lateThresholdMinutes: Int,
        missedCutoffHour: Int,
        checkInRadiusMeters: Int
    ): Result<Unit> = runCatching {
        requirePermission(actor, PermissionKey.CONFIGURE_SETTINGS)
        require(lateThresholdMinutes in 5..240) { "Late threshold must be between 5 and 240 minutes." }
        require(missedCutoffHour in 12..23) { "Missed cutoff hour must be between 12 and 23." }
        require(checkInRadiusMeters in 25..1000) { "Check-in radius must be between 25 and 1000 metres." }

        val current = settingsDao.getSettings() ?: AppSettingsEntity()
        settingsDao.upsertSettings(
            current.copy(
                lateThresholdMinutes = lateThresholdMinutes,
                missedCutoffHour = missedCutoffHour,
                checkInRadiusMeters = checkInRadiusMeters
            )
        )
        logAction(actor, "UPDATE_SETTINGS", "Late=${lateThresholdMinutes}m, Cutoff=${missedCutoffHour}:00, Radius=${checkInRadiusMeters}m")
    }

    suspend fun sendAdminAnnouncement(actor: UserEntity, title: String, message: String): Result<Unit> = runCatching {
        requirePermission(actor, PermissionKey.SEND_ANNOUNCEMENTS)
        require(title.trim().isNotEmpty() && message.trim().isNotEmpty()) { "Title and message are required." }
        sendNotification(
            targetUserId = null,
            targetRole = null,
            type = NotificationType.ANNOUNCEMENT,
            title = title.trim(),
            message = message.trim()
        )
        logAction(actor, "ANNOUNCEMENT", "${title.trim()}: ${message.trim()}")
    }

    suspend fun syncPendingRecords(actor: UserEntity?): Int {
        val pendingCount = assignmentDao.getPendingSyncAssignments().size +
            visitDao.getPendingSyncVisits().size +
            taskDao.getPendingSyncTasks().size +
            attendanceDao.getPendingSyncAttendance().size
        if (pendingCount > 0) {
            assignmentDao.markAllSynced()
            visitDao.markAllSynced()
            taskDao.markAllSynced()
            attendanceDao.markAllSynced()
            prefs.setLastSyncedAt(System.currentTimeMillis())
            if (actor != null) {
                logAction(actor, "OFFLINE_SYNC", "Synchronized $pendingCount queued record(s)")
            }
        }
        return pendingCount
    }

    suspend fun markNotificationRead(id: String) = notificationDao.markRead(id)
    suspend fun markAllNotificationsRead() = notificationDao.markAllRead()

    // --- Optional Quick Sample Operations Loader for Main Super Admin ---
    suspend fun seedSampleOperationsData(actor: UserEntity): Result<Unit> = runCatching {
        require(actor.role == UserRole.MAIN_SUPER_ADMIN) { "Only Main Super Admin can load sample data." }
        val today = todayString()

        val admin = UserEntity("usr_admin_demo", "Aarav Mehta", "9800000001", UserRole.ADMIN, true, true)
        val manager = UserEntity("usr_mgr_demo", "Priya Nair", "9800000002", UserRole.MANAGER, true, true)
        val supervisor = UserEntity("usr_sup_demo", "Rohan Verma", "9800000003", UserRole.SUPERVISOR, true, true)
        val visitor = UserEntity(
            id = "usr_vis_demo",
            name = "Vikram Singh",
            mobileNumber = "9800000004",
            role = UserRole.VISITOR,
            isApproved = true,
            isActive = true,
            daySiName = "Rajesh Kumar",
            daySiMobile = "9811112201",
            nightSiName = "Suresh Yadav",
            nightSiMobile = "9811112202",
            dhmName = "Ankit Sharma",
            dhmMobile = "9811112203",
            clmName = "Neha Gupta",
            clmMobile = "9811112204"
        )
        userDao.upsertUsers(listOf(admin, manager, supervisor, visitor))

        val stores = listOf(
            StoreEntity("store_1", "Metro Flagship Mart", "MG Road, Sector 14", 28.4595, 77.0266, "Karan Patel", "9822001101"),
            StoreEntity("store_2", "City Center Hyper", "Cyber Hub Plaza", 28.4950, 77.0895, "Meera Joshi", "9822001102"),
            StoreEntity("store_3", "Green Park Retail", "Main Market Block C", 28.5590, 77.2066, "Dev Kapoor", "9822001103")
        )
        storeDao.upsertStores(stores)

        val assignments = listOf(
            StoreAssignmentEntity("asgn_1", "store_1", visitor.id, today, "09:30", AssignmentStatus.PENDING, 1, actor.id, "Morning audit"),
            StoreAssignmentEntity("asgn_2", "store_2", visitor.id, today, "11:30", AssignmentStatus.PENDING, 2, actor.id, "Stock check"),
            StoreAssignmentEntity("asgn_3", "store_3", visitor.id, today, "14:00", AssignmentStatus.PENDING, 3, actor.id, "Evening review")
        )
        assignmentDao.upsertAssignments(assignments)

        taskDao.upsertTasks(
            listOf(
                TaskEntity("tsk_1", "store_1", "Metro Flagship Mart", "asgn_1", visitor.id, visitor.name, supervisor.id, supervisor.name, "Verify Front Display Planogram", "Confirm promotional end-cap stock & pricing tags", TaskStatus.PENDING, dueDate = today),
                TaskEntity("tsk_2", "store_2", "City Center Hyper", "asgn_2", visitor.id, visitor.name, manager.id, manager.name, "Collect POS Settlement Slip", "Upload photo if mismatch occurs", TaskStatus.PENDING, dueDate = today)
            )
        )

        sendNotification(
            targetUserId = null,
            type = NotificationType.ANNOUNCEMENT,
            title = "Sample Operations Loaded",
            message = "Test accounts created: Admin (9800000001), Manager (9800000002), Supervisor (9800000003), Visitor (9800000004)."
        )
        logAction(actor, "LOAD_SAMPLE_DATA", "Populated sample team, 3 stores, assignments, and tasks")
    }

    private suspend fun sendNotification(
        targetUserId: String? = null,
        targetRole: String? = null,
        type: NotificationType,
        title: String,
        message: String
    ) {
        val item = AppNotificationEntity(
            id = "notif_${UUID.randomUUID().toString().take(8)}",
            targetUserId = targetUserId,
            targetRole = targetRole,
            type = type,
            title = title,
            message = message
        )
        notificationDao.insertNotification(item)
        notificationHelper.showSystemNotification(title, message)
    }

    private suspend fun logAction(actor: UserEntity, action: String, details: String) {
        activityLogDao.insertLog(
            ActivityLogEntity(
                id = "log_${UUID.randomUUID().toString().take(8)}",
                actorId = actor.id,
                actorName = actor.name,
                actorRole = actor.role.displayName,
                action = action,
                details = details
            )
        )
    }

    private suspend fun requirePermission(user: UserEntity, key: PermissionKey) {
        if (user.role == UserRole.MAIN_SUPER_ADMIN) return
        val perms = permissionDao.getPermissionForRole(user.role.name)
            ?: RolePermissionEntity.defaultsFor(user.role)
        val msaPerms = permissionDao.getPermissionForRole(UserRole.MAIN_SUPER_ADMIN.name)
            ?: RolePermissionEntity.defaultsFor(UserRole.MAIN_SUPER_ADMIN)
        // Admin cannot exceed Main Super Admin grants
        require(perms.hasPermission(key) && msaPerms.hasPermission(key)) {
            "Permission denied: ${key.label} is not enabled for ${user.role.displayName}."
        }
    }

    private fun isNowPastThreshold(assignedDate: String, scheduledTime: String, thresholdMinutes: Int, nowMillis: Long): Boolean {
        return try {
            val parsedDate = dateFormatter.parse(assignedDate) ?: return false
            val parts = scheduledTime.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
            val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val cal = Calendar.getInstance().apply {
                time = parsedDate
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                add(Calendar.MINUTE, thresholdMinutes)
            }
            nowMillis > cal.timeInMillis
        } catch (_: Exception) {
            false
        }
    }

    private fun normalizeMobile(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }
}
