package com.example.service

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class PhotoStorageHelper(private val context: Context) {

    fun createCameraCaptureUri(subdir: String = "checkin_photos"): Pair<Uri, File> {
        val dir = File(context.filesDir, subdir).apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    fun copyPickerUriToInternal(sourceUri: Uri, subdir: String = "task_photos"): String? {
        return try {
            val dir = File(context.filesDir, subdir).apply { mkdirs() }
            val destFile = File(dir, "picked_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            ).toString()
        } catch (_: Exception) {
            null
        }
    }
}
