package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observeUserById(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE mobileNumber = :mobile LIMIT 1")
    suspend fun getUserByMobile(mobile: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE role = 'MAIN_SUPER_ADMIN'")
    fun observeMainSuperAdminCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM users WHERE role = 'MAIN_SUPER_ADMIN'")
    suspend fun getMainSuperAdminCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsers(users: List<UserEntity>)
}

@Dao
interface RolePermissionDao {
    @Query("SELECT * FROM role_permissions")
    fun observeAllRolePermissions(): Flow<List<RolePermissionEntity>>

    @Query("SELECT * FROM role_permissions WHERE roleName = :roleName LIMIT 1")
    fun observePermissionForRole(roleName: String): Flow<RolePermissionEntity?>

    @Query("SELECT * FROM role_permissions WHERE roleName = :roleName LIMIT 1")
    suspend fun getPermissionForRole(roleName: String): RolePermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPermission(permission: RolePermissionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPermissions(permissions: List<RolePermissionEntity>)
}

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores ORDER BY name ASC")
    fun observeAllStores(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores WHERE id = :id LIMIT 1")
    suspend fun getStoreById(id: String): StoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStore(store: StoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStores(stores: List<StoreEntity>)

    @Query("DELETE FROM stores WHERE id = :id")
    suspend fun deleteStoreById(id: String)
}

@Dao
interface StoreAssignmentDao {
    @Query("SELECT * FROM store_assignments ORDER BY assignedDate DESC, sequenceOrder ASC")
    fun observeAllAssignments(): Flow<List<StoreAssignmentEntity>>

    @Query("SELECT * FROM store_assignments WHERE visitorId = :visitorId ORDER BY assignedDate DESC, sequenceOrder ASC")
    fun observeAssignmentsForVisitor(visitorId: String): Flow<List<StoreAssignmentEntity>>

    @Query("SELECT * FROM store_assignments WHERE id = :id LIMIT 1")
    suspend fun getAssignmentById(id: String): StoreAssignmentEntity?

    @Query("SELECT * FROM store_assignments WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncAssignments(): List<StoreAssignmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(assignment: StoreAssignmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignments(assignments: List<StoreAssignmentEntity>)

    @Query("UPDATE store_assignments SET syncStatus = 'SYNCED' WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun markAllSynced()
}

@Dao
interface VisitRecordDao {
    @Query("SELECT * FROM visit_records ORDER BY visitDate DESC, checkInTime DESC")
    fun observeAllVisits(): Flow<List<VisitRecordEntity>>

    @Query("SELECT * FROM visit_records WHERE assignmentId = :assignmentId LIMIT 1")
    suspend fun getVisitByAssignmentId(assignmentId: String): VisitRecordEntity?

    @Query("SELECT * FROM visit_records WHERE submissionKey = :submissionKey LIMIT 1")
    suspend fun getVisitBySubmissionKey(submissionKey: String): VisitRecordEntity?

    @Query("SELECT * FROM visit_records WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncVisits(): List<VisitRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVisit(visit: VisitRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVisits(visits: List<VisitRecordEntity>)

    @Query("UPDATE visit_records SET syncStatus = 'SYNCED' WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun markAllSynced()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncTasks(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Query("UPDATE tasks SET syncStatus = 'SYNCED' WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun markAllSynced()
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance ORDER BY date DESC, markedAt DESC")
    fun observeAllAttendance(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE visitorId = :visitorId AND date = :date LIMIT 1")
    suspend fun getAttendanceForVisitorOnDate(visitorId: String, date: String): AttendanceEntity?

    @Query("SELECT * FROM attendance WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun getPendingSyncAttendance(): List<AttendanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendance(attendance: AttendanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendanceList(records: List<AttendanceEntity>)

    @Query("UPDATE attendance SET syncStatus = 'SYNCED' WHERE syncStatus = 'PENDING_SYNC'")
    suspend fun markAllSynced()
}

@Dao
interface RescheduleRequestDao {
    @Query("SELECT * FROM reschedule_requests ORDER BY createdAt DESC")
    fun observeAllRequests(): Flow<List<RescheduleRequestEntity>>

    @Query("SELECT * FROM reschedule_requests WHERE id = :id LIMIT 1")
    suspend fun getRequestById(id: String): RescheduleRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequest(request: RescheduleRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequests(requests: List<RescheduleRequestEntity>)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeAllNotifications(): Flow<List<AppNotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<AppNotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllRead()
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeRecentLogs(): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<ActivityLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActivityLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<ActivityLogEntity>)
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: AppSettingsEntity)
}
