package com.example.service

import android.content.Context
import android.net.Uri
import com.example.data.local.ActivityLogEntity
import com.example.data.local.AppDatabase
import com.example.data.local.AppNotificationEntity
import com.example.data.local.AppSettingsEntity
import com.example.data.local.AttendanceEntity
import com.example.data.local.RescheduleRequestEntity
import com.example.data.local.RolePermissionEntity
import com.example.data.local.StoreAssignmentEntity
import com.example.data.local.StoreEntity
import com.example.data.local.TaskEntity
import com.example.data.local.UserEntity
import com.example.data.local.VisitRecordEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first

@JsonClass(generateAdapter = true)
data class DatabaseBackupSnapshot(
    val exportedAt: Long,
    val appVersion: String = "1.0",
    val users: List<UserEntity>,
    val rolePermissions: List<RolePermissionEntity>,
    val stores: List<StoreEntity>,
    val assignments: List<StoreAssignmentEntity>,
    val visits: List<VisitRecordEntity>,
    val tasks: List<TaskEntity>,
    val attendance: List<AttendanceEntity>,
    val rescheduleRequests: List<RescheduleRequestEntity>,
    val notifications: List<AppNotificationEntity>,
    val activityLogs: List<ActivityLogEntity>,
    val settings: AppSettingsEntity?
)

class BackupManager(
    private val context: Context,
    private val db: AppDatabase
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(DatabaseBackupSnapshot::class.java).indent("  ")

    suspend fun exportBackupToUri(destUri: Uri): Result<String> = runCatching {
        val snapshot = DatabaseBackupSnapshot(
            exportedAt = System.currentTimeMillis(),
            users = db.userDao().observeAllUsers().first(),
            rolePermissions = db.rolePermissionDao().observeAllRolePermissions().first(),
            stores = db.storeDao().observeAllStores().first(),
            assignments = db.storeAssignmentDao().observeAllAssignments().first(),
            visits = db.visitRecordDao().observeAllVisits().first(),
            tasks = db.taskDao().observeAllTasks().first(),
            attendance = db.attendanceDao().observeAllAttendance().first(),
            rescheduleRequests = db.rescheduleRequestDao().observeAllRequests().first(),
            notifications = db.notificationDao().observeAllNotifications().first(),
            activityLogs = db.activityLogDao().getAllLogs(),
            settings = db.appSettingsDao().getSettings()
        )
        val json = adapter.toJson(snapshot)
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open backup destination")

        val summary = "${snapshot.users.size} users, ${snapshot.stores.size} stores, ${snapshot.visits.size} visits"
        val currentSettings = db.appSettingsDao().getSettings() ?: AppSettingsEntity()
        db.appSettingsDao().upsertSettings(
            currentSettings.copy(
                lastBackupAt = snapshot.exportedAt,
                backupSummary = summary
            )
        )
        summary
    }

    suspend fun restoreBackupFromUri(sourceUri: Uri): Result<String> = runCatching {
        val json = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Unable to read backup file")
        val snapshot = adapter.fromJson(json) ?: error("Invalid backup JSON format")

        db.userDao().upsertUsers(snapshot.users)
        db.rolePermissionDao().upsertPermissions(snapshot.rolePermissions)
        db.storeDao().upsertStores(snapshot.stores)
        db.storeAssignmentDao().upsertAssignments(snapshot.assignments)
        db.visitRecordDao().upsertVisits(snapshot.visits)
        db.taskDao().upsertTasks(snapshot.tasks)
        db.attendanceDao().upsertAttendanceList(snapshot.attendance)
        db.rescheduleRequestDao().upsertRequests(snapshot.rescheduleRequests)
        db.notificationDao().insertNotifications(snapshot.notifications)
        db.activityLogDao().insertLogs(snapshot.activityLogs)
        snapshot.settings?.let { db.appSettingsDao().upsertSettings(it) }

        "Restored ${snapshot.users.size} users, ${snapshot.stores.size} stores, ${snapshot.visits.size} visits"
    }
}
