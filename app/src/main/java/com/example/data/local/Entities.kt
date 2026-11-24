package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.squareup.moshi.JsonClass

class RoomConverters {
    @TypeConverter fun fromRole(value: UserRole): String = value.name
    @TypeConverter fun toRole(value: String): UserRole = UserRole.entries.firstOrNull { it.name == value } ?: UserRole.VISITOR

    @TypeConverter fun fromAssignmentStatus(value: AssignmentStatus): String = value.name
    @TypeConverter fun toAssignmentStatus(value: String): AssignmentStatus = AssignmentStatus.entries.firstOrNull { it.name == value } ?: AssignmentStatus.PENDING

    @TypeConverter fun fromTaskStatus(value: TaskStatus): String = value.name
    @TypeConverter fun toTaskStatus(value: String): TaskStatus = TaskStatus.entries.firstOrNull { it.name == value } ?: TaskStatus.PENDING

    @TypeConverter fun fromAttendanceStatus(value: AttendanceStatus): String = value.name
    @TypeConverter fun toAttendanceStatus(value: String): AttendanceStatus = AttendanceStatus.entries.firstOrNull { it.name == value } ?: AttendanceStatus.PRESENT

    @TypeConverter fun fromRequestType(value: RequestType): String = value.name
    @TypeConverter fun toRequestType(value: String): RequestType = RequestType.entries.firstOrNull { it.name == value } ?: RequestType.RESCHEDULE

    @TypeConverter fun fromApprovalStatus(value: ApprovalStatus): String = value.name
    @TypeConverter fun toApprovalStatus(value: String): ApprovalStatus = ApprovalStatus.entries.firstOrNull { it.name == value } ?: ApprovalStatus.PENDING

    @TypeConverter fun fromNotificationType(value: NotificationType): String = value.name
    @TypeConverter fun toNotificationType(value: String): NotificationType = NotificationType.entries.firstOrNull { it.name == value } ?: NotificationType.ANNOUNCEMENT

    @TypeConverter fun fromSyncStatus(value: SyncStatus): String = value.name
    @TypeConverter fun toSyncStatus(value: String): SyncStatus = SyncStatus.entries.firstOrNull { it.name == value } ?: SyncStatus.SYNCED
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mobileNumber: String,
    val role: UserRole,
    val isApproved: Boolean = true,
    val isActive: Boolean = true,
    val daySiName: String = "",
    val daySiMobile: String = "",
    val nightSiName: String = "",
    val nightSiMobile: String = "",
    val dhmName: String = "",
    val dhmMobile: String = "",
    val clmName: String = "",
    val clmMobile: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "role_permissions")
data class RolePermissionEntity(
    @PrimaryKey val roleName: String,
    val canManageUsers: Boolean = false,
    val canManageStores: Boolean = false,
    val canAssignStores: Boolean = false,
    val canCreateTasks: Boolean = false,
    val canMarkAttendance: Boolean = false,
    val canApproveReschedule: Boolean = false,
    val canViewReports: Boolean = false,
    val canExportReports: Boolean = false,
    val canSendAnnouncements: Boolean = false,
    val canConfigureSettings: Boolean = false,
    val canCheckInOut: Boolean = false,
    val canViewLiveMap: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun hasPermission(key: PermissionKey): Boolean = when (key) {
        PermissionKey.MANAGE_USERS -> canManageUsers
        PermissionKey.MANAGE_STORES -> canManageStores
        PermissionKey.ASSIGN_STORES -> canAssignStores
        PermissionKey.CREATE_TASKS -> canCreateTasks
        PermissionKey.MARK_ATTENDANCE -> canMarkAttendance
        PermissionKey.APPROVE_RESCHEDULE -> canApproveReschedule
        PermissionKey.VIEW_REPORTS -> canViewReports
        PermissionKey.EXPORT_REPORTS -> canExportReports
        PermissionKey.SEND_ANNOUNCEMENTS -> canSendAnnouncements
        PermissionKey.CONFIGURE_SETTINGS -> canConfigureSettings
        PermissionKey.CHECK_IN_OUT -> canCheckInOut
        PermissionKey.VIEW_LIVE_MAP -> canViewLiveMap
    }

    fun withPermission(key: PermissionKey, enabled: Boolean): RolePermissionEntity = when (key) {
        PermissionKey.MANAGE_USERS -> copy(canManageUsers = enabled)
        PermissionKey.MANAGE_STORES -> copy(canManageStores = enabled)
        PermissionKey.ASSIGN_STORES -> copy(canAssignStores = enabled)
        PermissionKey.CREATE_TASKS -> copy(canCreateTasks = enabled)
        PermissionKey.MARK_ATTENDANCE -> copy(canMarkAttendance = enabled)
        PermissionKey.APPROVE_RESCHEDULE -> copy(canApproveReschedule = enabled)
        PermissionKey.VIEW_REPORTS -> copy(canViewReports = enabled)
        PermissionKey.EXPORT_REPORTS -> copy(canExportReports = enabled)
        PermissionKey.SEND_ANNOUNCEMENTS -> copy(canSendAnnouncements = enabled)
        PermissionKey.CONFIGURE_SETTINGS -> copy(canConfigureSettings = enabled)
        PermissionKey.CHECK_IN_OUT -> copy(canCheckInOut = enabled)
        PermissionKey.VIEW_LIVE_MAP -> copy(canViewLiveMap = enabled)
    }.copy(updatedAt = System.currentTimeMillis())

    companion object {
        fun defaultsFor(role: UserRole): RolePermissionEntity = when (role) {
            UserRole.MAIN_SUPER_ADMIN -> RolePermissionEntity(
                roleName = role.name,
                canManageUsers = true,
                canManageStores = true,
                canAssignStores = true,
                canCreateTasks = true,
                canMarkAttendance = true,
                canApproveReschedule = true,
                canViewReports = true,
                canExportReports = true,
                canSendAnnouncements = true,
                canConfigureSettings = true,
                canCheckInOut = true,
                canViewLiveMap = true
            )
            UserRole.ADMIN -> RolePermissionEntity(
                roleName = role.name,
                canManageUsers = true,
                canManageStores = true,
                canAssignStores = true,
                canCreateTasks = true,
                canMarkAttendance = true,
                canApproveReschedule = true,
                canViewReports = true,
                canExportReports = true,
                canSendAnnouncements = true,
                canConfigureSettings = false,
                canCheckInOut = false,
                canViewLiveMap = true
            )
            UserRole.MANAGER -> RolePermissionEntity(
                roleName = role.name,
                canManageUsers = false,
                canManageStores = false,
                canAssignStores = true,
                canCreateTasks = true,
                canMarkAttendance = true,
                canApproveReschedule = true,
                canViewReports = true,
                canExportReports = true,
                canSendAnnouncements = true,
                canConfigureSettings = false,
                canCheckInOut = false,
                canViewLiveMap = true
            )
            UserRole.SUPERVISOR -> RolePermissionEntity(
                roleName = role.name,
                canManageUsers = false,
                canManageStores = false,
                canAssignStores = false,
                canCreateTasks = true,
                canMarkAttendance = true,
                canApproveReschedule = true,
                canViewReports = true,
                canExportReports = true,
                canSendAnnouncements = false,
                canConfigureSettings = false,
                canCheckInOut = false,
                canViewLiveMap = true
            )
            UserRole.VISITOR -> RolePermissionEntity(
                roleName = role.name,
                canManageUsers = false,
                canManageStores = false,
                canAssignStores = false,
                canCreateTasks = false,
                canMarkAttendance = false,
                canApproveReschedule = false,
                canViewReports = false,
                canExportReports = false,
                canSendAnnouncements = false,
                canConfigureSettings = false,
                canCheckInOut = true,
                canViewLiveMap = true
            )
        }
    }
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val contactName: String,
    val contactPhone: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "store_assignments")
data class StoreAssignmentEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val visitorId: String,
    val assignedDate: String,
    val scheduledTime: String,
    val status: AssignmentStatus = AssignmentStatus.PENDING,
    val sequenceOrder: Int = 1,
    val assignedByUserId: String,
    val notes: String = "",
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "visit_records")
data class VisitRecordEntity(
    @PrimaryKey val id: String,
    val assignmentId: String,
    val storeId: String,
    val storeName: String,
    val visitorId: String,
    val visitorName: String,
    val visitDate: String,
    val checkInTime: Long? = null,
    val checkInLat: Double? = null,
    val checkInLng: Double? = null,
    val distanceMetersAtCheckIn: Double? = null,
    val checkInPhotoUri: String? = null,
    val daySiName: String = "",
    val daySiMobile: String = "",
    val nightSiName: String = "",
    val nightSiMobile: String = "",
    val dhmName: String = "",
    val dhmMobile: String = "",
    val clmName: String = "",
    val clmMobile: String = "",
    val checkOutTime: Long? = null,
    val totalGainAmount: Double? = null,
    val reportSubmitted: Boolean = false,
    val reportSubmittedAt: Long? = null,
    val isLate: Boolean = false,
    val isMissed: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val submissionKey: String = ""
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val storeName: String,
    val assignmentId: String? = null,
    val visitorId: String,
    val visitorName: String,
    val createdByUserId: String,
    val createdByName: String,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val remark: String = "",
    val photoUri: String? = null,
    val dueDate: String,
    val completedAt: Long? = null,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "attendance")
data class AttendanceEntity(
    @PrimaryKey val id: String,
    val visitorId: String,
    val visitorName: String,
    val date: String,
    val status: AttendanceStatus,
    val markedByUserId: String,
    val markedByName: String,
    val notes: String = "",
    val markedAt: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "reschedule_requests")
data class RescheduleRequestEntity(
    @PrimaryKey val id: String,
    val assignmentId: String,
    val storeId: String,
    val storeName: String,
    val visitorId: String,
    val visitorName: String,
    val requestType: RequestType,
    val requestedDate: String? = null,
    val requestedTime: String? = null,
    val reason: String,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val reviewedByUserId: String? = null,
    val reviewedByName: String? = null,
    val reviewNote: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "notifications")
data class AppNotificationEntity(
    @PrimaryKey val id: String,
    val targetUserId: String? = null,
    val targetRole: String? = null,
    val type: NotificationType,
    val title: String,
    val message: String,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val actorId: String,
    val actorName: String,
    val actorRole: String,
    val action: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val lateThresholdMinutes: Int = 30,
    val missedCutoffHour: Int = 20,
    val checkInRadiusMeters: Int = 100,
    val lastBackupAt: Long? = null,
    val backupSummary: String = ""
)
