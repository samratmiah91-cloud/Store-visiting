package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.SessionPreferences
import com.example.data.repository.StoreVisitRepository
import com.example.service.BackupManager
import com.example.service.ConnectivityObserver
import com.example.service.LocationService
import com.example.service.NotificationHelper
import com.example.service.PhotoStorageHelper
import com.example.service.ReportExporter

class StoreVisitApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val sessionPreferences by lazy { SessionPreferences(this) }
    val notificationHelper by lazy { NotificationHelper(this) }
    val locationService by lazy { LocationService(this) }
    val connectivityObserver by lazy { ConnectivityObserver(this) }
    val photoStorageHelper by lazy { PhotoStorageHelper(this) }
    val reportExporter by lazy { ReportExporter(this) }
    val backupManager by lazy { BackupManager(this, database) }
    val repository by lazy { StoreVisitRepository(database, sessionPreferences, notificationHelper) }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannel()
    }
}
