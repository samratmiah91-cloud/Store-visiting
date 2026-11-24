package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.AttendanceEntity
import com.example.data.local.StoreAssignmentEntity
import com.example.data.local.TaskEntity
import com.example.data.local.VisitRecordEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportExportPayload(
    val title: String,
    val filterSummary: String,
    val visits: List<VisitRecordEntity>,
    val assignments: List<StoreAssignmentEntity>,
    val tasks: List<TaskEntity>,
    val attendance: List<AttendanceEntity>
)

class ReportExporter(private val context: Context) {
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())

    fun exportExcelCsv(payload: ReportExportPayload): Result<Uri> = runCatching {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "StoreVisit_Report_${fileStampFormat.format(Date())}.csv")
        val sb = StringBuilder()
        // UTF-8 BOM so Excel opens ₹ and Hindi text cleanly
        sb.append('\uFEFF')
        sb.appendLine("\"StoreVisit Operations Report\",\"${escapeCsv(payload.title)}\"")
        sb.appendLine("\"Filters\",\"${escapeCsv(payload.filterSummary)}\"")
        sb.appendLine("\"Generated At\",\"${escapeCsv(dateTimeFormat.format(Date()))}\"")
        sb.appendLine()

        val totalGain = payload.visits.sumOf { it.totalGainAmount ?: 0.0 }
        val completedCount = payload.visits.count { it.reportSubmitted }
        val pendingCount = payload.assignments.count {
            it.status.name == "PENDING" || it.status.name == "CHECKED_IN"
        }
        sb.appendLine("\"Summary Metric\",\"Value\"")
        sb.appendLine("\"Total Gain (INR)\",\"${String.format(Locale.US, "%.2f", totalGain)}\"")
        sb.appendLine("\"Completed Visits\",\"$completedCount\"")
        sb.appendLine("\"Pending Visits\",\"$pendingCount\"")
        sb.appendLine("\"Tasks Completed\",\"${payload.tasks.count { it.status.name == "COMPLETED" }} / ${payload.tasks.size}\"")
        sb.appendLine("\"Attendance Records\",\"${payload.attendance.size}\"")
        sb.appendLine()

        sb.appendLine("VISIT REPORTS")
        sb.appendLine("Visit ID,Date,Store Name,Visitor Name,Check-In Time,Check-In GPS,Distance(m),Check-Out Time,Total Gain (INR),Report Status,Late,Missed,Day SI,Night SI,DHM,CLM,Check-In Photo URI")
        payload.visits.forEach { v ->
            val checkInStr = v.checkInTime?.let { dateTimeFormat.format(Date(it)) } ?: "-"
            val checkOutStr = v.checkOutTime?.let { dateTimeFormat.format(Date(it)) } ?: "-"
            val gpsStr = if (v.checkInLat != null && v.checkInLng != null) {
                "${String.format(Locale.US, "%.5f", v.checkInLat)}, ${String.format(Locale.US, "%.5f", v.checkInLng)}"
            } else "-"
            val distStr = v.distanceMetersAtCheckIn?.toInt()?.toString() ?: "-"
            val gainStr = v.totalGainAmount?.let { String.format(Locale.US, "%.2f", it) } ?: "0.00"
            val statusStr = if (v.reportSubmitted) "Submitted" else "In Progress"
            val daySi = "${v.daySiName} (${v.daySiMobile})".trim()
            val nightSi = "${v.nightSiName} (${v.nightSiMobile})".trim()
            val dhm = "${v.dhmName} (${v.dhmMobile})".trim()
            val clm = "${v.clmName} (${v.clmMobile})".trim()
            sb.appendLine(
                listOf(
                    v.id, v.visitDate, v.storeName, v.visitorName, checkInStr, gpsStr,
                    distStr, checkOutStr, gainStr, statusStr,
                    v.isLate.toString(), v.isMissed.toString(),
                    daySi, nightSi, dhm, clm, v.checkInPhotoUri ?: "-"
                ).joinToString(",") { "\"${escapeCsv(it)}\"" }
            )
        }

        sb.appendLine()
        sb.appendLine("TASK COMPLETION REPORT")
        sb.appendLine("Task ID,Due Date,Store,Visitor,Title,Status,Remark,Completed At,Photo URI")
        payload.tasks.forEach { t ->
            val completedStr = t.completedAt?.let { dateTimeFormat.format(Date(it)) } ?: "-"
            sb.appendLine(
                listOf(
                    t.id, t.dueDate, t.storeName, t.visitorName, t.title,
                    t.status.label, t.remark, completedStr, t.photoUri ?: "-"
                ).joinToString(",") { "\"${escapeCsv(it)}\"" }
            )
        }

        sb.appendLine()
        sb.appendLine("ATTENDANCE REPORT")
        sb.appendLine("Date,Visitor,Status,Marked By,Notes,Marked At")
        payload.attendance.forEach { a ->
            sb.appendLine(
                listOf(
                    a.date, a.visitorName, a.status.label, a.markedByName,
                    a.notes, dateTimeFormat.format(Date(a.markedAt))
                ).joinToString(",") { "\"${escapeCsv(it)}\"" }
            )
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportPdf(payload: ReportExportPayload): Result<Uri> = runCatching {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "StoreVisit_Report_${fileStampFormat.format(Date())}.pdf")
        val document = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val lineHeight = 16f

        val titlePaint = Paint().apply {
            color = Color.rgb(15, 92, 84)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headingPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(51, 65, 85)
            textSize = 9.5f
        }
        val mutedPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8.5f
        }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin + 12f

        fun newPageIfNeeded(requiredSpace: Float = 28f) {
            if (y + requiredSpace > pageHeight - margin) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin + 12f
            }
        }

        canvas.drawText("StoreVisit Operations Report", margin, y, titlePaint)
        y += 18f
        canvas.drawText("Filters: ${payload.filterSummary}", margin, y, mutedPaint)
        y += 14f
        canvas.drawText("Generated: ${dateTimeFormat.format(Date())}", margin, y, mutedPaint)
        y += 22f

        val totalGain = payload.visits.sumOf { it.totalGainAmount ?: 0.0 }
        val completedCount = payload.visits.count { it.reportSubmitted }
        val pendingCount = payload.assignments.count {
            it.status.name == "PENDING" || it.status.name == "CHECKED_IN"
        }

        canvas.drawText("Executive Summary", margin, y, headingPaint)
        y += lineHeight
        canvas.drawText(
            "Total Gain: Rs. ${String.format(Locale.US, "%.2f", totalGain)}   |   Completed Visits: $completedCount   |   Pending: $pendingCount",
            margin,
            y,
            bodyPaint
        )
        y += lineHeight
        canvas.drawText(
            "Tasks Completed: ${payload.tasks.count { it.status.name == "COMPLETED" }}/${payload.tasks.size}   |   Attendance Entries: ${payload.attendance.size}",
            margin,
            y,
            bodyPaint
        )
        y += 22f

        canvas.drawText("Store Visit Details (${payload.visits.size})", margin, y, headingPaint)
        y += lineHeight
        payload.visits.forEachIndexed { idx, v ->
            newPageIfNeeded(54f)
            val inStr = v.checkInTime?.let { dateTimeFormat.format(Date(it)) } ?: "-"
            val outStr = v.checkOutTime?.let { dateTimeFormat.format(Date(it)) } ?: "-"
            val gain = v.totalGainAmount?.let { "Rs. ${String.format(Locale.US, "%.2f", it)}" } ?: "Pending"
            val gps = if (v.checkInLat != null && v.checkInLng != null) {
                "${String.format(Locale.US, "%.4f", v.checkInLat)}, ${String.format(Locale.US, "%.4f", v.checkInLng)}"
            } else "N/A"
            canvas.drawText("${idx + 1}. ${v.storeName} (${v.visitDate}) — Visitor: ${v.visitorName}", margin, y, headingPaint)
            y += 13f
            canvas.drawText("   In: $inStr | Out: $outStr | Gain: $gain | GPS: $gps", margin, y, bodyPaint)
            y += 13f
            canvas.drawText(
                "   Day SI: ${v.daySiName.ifBlank { "-" }} | Night SI: ${v.nightSiName.ifBlank { "-" }} | DHM: ${v.dhmName.ifBlank { "-" }} | CLM: ${v.clmName.ifBlank { "-" }}",
                margin,
                y,
                mutedPaint
            )
            y += 16f
        }

        newPageIfNeeded(40f)
        y += 8f
        canvas.drawText("Task Completion (${payload.tasks.size})", margin, y, headingPaint)
        y += lineHeight
        payload.tasks.forEachIndexed { idx, t ->
            newPageIfNeeded(30f)
            canvas.drawText("${idx + 1}. [${t.status.label}] ${t.title} — ${t.storeName} (${t.visitorName})", margin, y, bodyPaint)
            y += 12f
            if (t.remark.isNotBlank()) {
                canvas.drawText("   Remark: ${t.remark.take(90)}", margin, y, mutedPaint)
                y += 12f
            }
        }

        newPageIfNeeded(40f)
        y += 8f
        canvas.drawText("Attendance Log (${payload.attendance.size})", margin, y, headingPaint)
        y += lineHeight
        payload.attendance.forEach { a ->
            newPageIfNeeded(20f)
            canvas.drawText("${a.date} | ${a.visitorName} — ${a.status.label} (by ${a.markedByName})", margin, y, bodyPaint)
            y += 13f
        }

        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareExportUri(uri: Uri, mimeType: String, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun escapeCsv(raw: String): String = raw.replace("\"", "\"\"")
}
