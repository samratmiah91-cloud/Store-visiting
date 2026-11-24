package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        RolePermissionEntity::class,
        StoreEntity::class,
        StoreAssignmentEntity::class,
        VisitRecordEntity::class,
        TaskEntity::class,
        AttendanceEntity::class,
        RescheduleRequestEntity::class,
        AppNotificationEntity::class,
        ActivityLogEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun rolePermissionDao(): RolePermissionDao
    abstract fun storeDao(): StoreDao
    abstract fun storeAssignmentDao(): StoreAssignmentDao
    abstract fun visitRecordDao(): VisitRecordDao
    abstract fun taskDao(): TaskDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun rescheduleRequestDao(): RescheduleRequestDao
    abstract fun notificationDao(): NotificationDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "store_visit_db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getInstance(context)
                                ensureDefaults(database)
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        suspend fun ensureDefaults(database: AppDatabase) {
            val permissionDao = database.rolePermissionDao()
            UserRole.entries.forEach { role ->
                if (permissionDao.getPermissionForRole(role.name) == null) {
                    permissionDao.upsertPermission(RolePermissionEntity.defaultsFor(role))
                }
            }
            val settingsDao = database.appSettingsDao()
            if (settingsDao.getSettings() == null) {
                settingsDao.upsertSettings(AppSettingsEntity())
            }
        }
    }
}
