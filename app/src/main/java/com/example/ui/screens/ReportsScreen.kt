package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.AssignmentStatus
import com.example.data.local.AttendanceEntity
import com.example.data.local.RolePermissionEntity
import com.example.data.local.StoreAssignmentEntity
import com.example.data.local.StoreEntity
import com.example.data.local.TaskEntity
import com.example.data.local.TaskStatus
import com.example.data.local.UserEntity
import com.example.data.local.UserRole
import com.example.data.local.VisitRecordEntity
import com.example.service.ReportExportPayload
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatInr
import com.example.ui.components.statusColors
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TealPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportPeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    ALL("All Time")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    user: UserEntity,
    permissions: RolePermissionEntity,
    users: List<UserEntity>,
    stores: List<StoreEntity>,
    assignments: List<StoreAssignmentEntity>,
    visits: List<VisitRecordEntity>,
    tasks: List<TaskEntity>,
    attendance: List<AttendanceEntity>,
    todayDate: String,
    labels: LocalizedLabels,
    onExportExcelCsv: (ReportExportPayload) -> Unit,
    onExportPdf: (ReportExportPayload) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf(ReportPeriod.ALL) }
    var selectedVisitorId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedStoreId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedStatus by rememberSaveable { mutableStateOf<AssignmentStatus?>(null) }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }

    var visitorDropdown by remember { mutableStateOf(false) }
    var storeDropdown by remember { mutableStateOf(false) }

    val visitors = remember(users) { users.filter { it.role == UserRole.VISITOR } }

    val minDateStr = remember(period, todayDate) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        when (period) {
            ReportPeriod.DAILY -> todayDate
            ReportPeriod.WEEKLY -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                sdf.format(cal.time)
            }
            ReportPeriod.MONTHLY -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                sdf.format(cal.time)
            }
            ReportPeriod.ALL -> "0000-00-00"
        }
    }

    val filteredAssignments = remember(assignments, stores, users, searchQuery, minDateStr, selectedVisitorId, selectedStoreId, selectedStatus) {
        val storeMap = stores.associateBy { it.id }
        val userMap = users.associateBy { it.id }
        assignments.filter { a ->
            val storeName = storeMap[a.storeId]?.name ?: ""
            val visitorName = userMap[a.visitorId]?.name ?: ""
            a.assignedDate >= minDateStr &&
                (selectedVisitorId == null || a.visitorId == selectedVisitorId) &&
                (selectedStoreId == null || a.storeId == selectedStoreId) &&
                (selectedStatus == null || a.status == selectedStatus) &&
                (searchQuery.isBlank() || storeName.contains(searchQuery, true) || visitorName.contains(searchQuery, true))
        }
    }

    val filteredVisits = remember(visits, searchQuery, minDateStr, selectedVisitorId, selectedStoreId, selectedStatus) {
        visits.filter { v ->
            v.visitDate >= minDateStr &&
                (selectedVisitorId == null || v.visitorId == selectedVisitorId) &&
                (selectedStoreId == null || v.storeId == selectedStoreId) &&
                (selectedStatus == null || when (selectedStatus) {
                    AssignmentStatus.COMPLETED -> v.reportSubmitted
                    AssignmentStatus.CHECKED_IN -> v.checkInTime != null && !v.reportSubmitted
                    AssignmentStatus.LATE -> v.isLate
                    AssignmentStatus.MISSED -> v.isMissed
                    else -> true
                }) &&
                (searchQuery.isBlank() || v.storeName.contains(searchQuery, true) || v.visitorName.contains(searchQuery, true))
        }
    }

    val filteredTasks = remember(tasks, searchQuery, minDateStr, selectedVisitorId, selectedStoreId) {
        tasks.filter { t ->
            t.dueDate >= minDateStr &&
                (selectedVisitorId == null || t.visitorId == selectedVisitorId) &&
                (selectedStoreId == null || t.storeId == selectedStoreId) &&
                (searchQuery.isBlank() || t.storeName.contains(searchQuery, true) || t.visitorName.contains(searchQuery, true) || t.title.contains(searchQuery, true))
        }
    }

    val filteredAttendance = remember(attendance, searchQuery, minDateStr, selectedVisitorId) {
        attendance.filter { a ->
            a.date >= minDateStr &&
                (selectedVisitorId == null || a.visitorId == selectedVisitorId) &&
                (searchQuery.isBlank() || a.visitorName.contains(searchQuery, true))
        }
    }

    val totalGain = filteredVisits.sumOf { it.totalGainAmount ?: 0.0 }
    val completedCount = filteredVisits.count { it.reportSubmitted }
    val pendingCount = filteredAssignments.count { it.status == AssignmentStatus.PENDING || it.status == AssignmentStatus.CHECKED_IN || it.status == AssignmentStatus.LATE }

    val filterSummary = "${period.label} • Visitor: ${selectedVisitorId?.let { id -> visitors.find { it.id == id }?.name } ?: "All"} • Store: ${selectedStoreId?.let { id -> stores.find { it.id == id }?.name } ?: "All"}"
    val exportPayload = ReportExportPayload(
        title = "${period.label} Report",
        filterSummary = filterSummary,
        visits = filteredVisits,
        assignments = filteredAssignments,
        tasks = filteredTasks,
        attendance = filteredAttendance
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(labels.reports, style = MaterialTheme.typography.headlineSmall)
        }

        // Search & Filters
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("Search store or visitor…") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_search_input")
            )
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReportPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = period == p,
                        onClick = { period = p },
                        label = { Text(p.label) }
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(
                    expanded = visitorDropdown,
                    onExpandedChange = { visitorDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedVisitorId?.let { id -> visitors.find { it.id == id }?.name } ?: "All Visitors",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Visitor") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(visitorDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = visitorDropdown, onDismissRequest = { visitorDropdown = false }) {
                        DropdownMenuItem(text = { Text("All Visitors") }, onClick = { selectedVisitorId = null; visitorDropdown = false })
                        visitors.forEach { v ->
                            DropdownMenuItem(text = { Text(v.name) }, onClick = { selectedVisitorId = v.id; visitorDropdown = false })
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = storeDropdown,
                    onExpandedChange = { storeDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedStoreId?.let { id -> stores.find { it.id == id }?.name } ?: "All Stores",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Store") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(storeDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = storeDropdown, onDismissRequest = { storeDropdown = false }) {
                        DropdownMenuItem(text = { Text("All Stores") }, onClick = { selectedStoreId = null; storeDropdown = false })
                        stores.forEach { s ->
                            DropdownMenuItem(text = { Text(s.name) }, onClick = { selectedStoreId = s.id; storeDropdown = false })
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedStatus == null,
                    onClick = { selectedStatus = null },
                    label = { Text("All Statuses") }
                )
                listOf(
                    AssignmentStatus.COMPLETED,
                    AssignmentStatus.PENDING,
                    AssignmentStatus.CHECKED_IN,
                    AssignmentStatus.LATE,
                    AssignmentStatus.MISSED
                ).forEach { st ->
                    FilterChip(
                        selected = selectedStatus == st,
                        onClick = { selectedStatus = st },
                        label = { Text(st.label) }
                    )
                }
            }
        }

        // KPI Summary
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiMetricCard(
                    title = "Total Gain",
                    value = formatInr(totalGain),
                    icon = Icons.Filled.CurrencyRupee,
                    accentColor = StatusSuccess,
                    subtitle = "$completedCount completed",
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Pending Visits",
                    value = pendingCount.toString(),
                    icon = Icons.Filled.Assessment,
                    accentColor = StatusInfo,
                    subtitle = "${filteredTasks.count { it.status == TaskStatus.COMPLETED }}/${filteredTasks.size} tasks",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Export Buttons
        if (permissions.canExportReports || user.role == UserRole.MAIN_SUPER_ADMIN) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onExportExcelCsv(exportPayload) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_excel_button")
                    ) {
                        Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Excel (CSV)")
                    }
                    OutlinedButton(
                        onClick = { onExportPdf(exportPayload) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export PDF")
                    }
                }
            }
        }

        // Report Sub-tabs: Visits, Visitor-wise, Store-wise, Tasks, Attendance
        item {
            val tabTitles = listOf("Visits", "Visitors", "Stores", "Tasks", "Attendance")
            TabRow(selectedTabIndex = activeTab) {
                tabTitles.forEachIndexed { idx, title ->
                    Tab(
                        selected = activeTab == idx,
                        onClick = { activeTab = idx },
                        text = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }

        when (activeTab) {
            0 -> {
                if (filteredVisits.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Assessment,
                            title = "No Visit Reports Found",
                            message = "Submitted check-in/check-out records appear here with GPS coordinates, check-in photo, and Total Gain."
                        )
                    }
                } else {
                    items(filteredVisits, key = { it.id }) { visit ->
                        val statusLabel = when {
                            visit.isMissed -> "Missed"
                            visit.reportSubmitted -> "Submitted"
                            else -> "Checked In"
                        }
                        val (fg, bg) = statusColors(statusLabel)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${visit.storeName} (${visit.visitDate})", style = MaterialTheme.typography.titleMedium)
                                        Text("Visitor: ${visit.visitorName}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusPill(text = statusLabel, color = fg, backgroundColor = bg)
                                }
                                Text(
                                    text = "In: ${formatDateTime(visit.checkInTime)} • Out: ${formatDateTime(visit.checkOutTime)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (visit.checkInLat != null && visit.checkInLng != null) {
                                    Text(
                                        text = "GPS: ${String.format(Locale.US, "%.5f", visit.checkInLat)}, ${String.format(Locale.US, "%.5f", visit.checkInLng)} (${visit.distanceMetersAtCheckIn?.toInt() ?: 0} m)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TealPrimary
                                    )
                                }
                                Text(
                                    text = "Total Gain: ${formatInr(visit.totalGainAmount ?: 0.0)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StatusSuccess
                                )
                                Text(
                                    text = "Day SI: ${visit.daySiName} (${visit.daySiMobile}) • Night SI: ${visit.nightSiName} (${visit.nightSiMobile})\nDHM: ${visit.dhmName} (${visit.dhmMobile}) • CLM: ${visit.clmName} (${visit.clmMobile})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!visit.checkInPhotoUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = Uri.parse(visit.checkInPhotoUri),
                                        contentDescription = "Visit check-in photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                val grouped = filteredVisits.groupBy { it.visitorId }
                items(visitors, key = { it.id }) { v ->
                    val vVisits = grouped[v.id].orEmpty()
                    val gain = vVisits.sumOf { it.totalGainAmount ?: 0.0 }
                    val completed = vVisits.count { it.reportSubmitted }
                    val pending = filteredAssignments.count { it.visitorId == v.id && (it.status == AssignmentStatus.PENDING || it.status == AssignmentStatus.LATE) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(v.name, style = MaterialTheme.typography.titleMedium)
                                Text("Completed: $completed • Pending: $pending", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(formatInr(gain), style = MaterialTheme.typography.titleMedium, color = StatusSuccess)
                        }
                    }
                }
            }
            2 -> {
                val grouped = filteredVisits.groupBy { it.storeId }
                items(stores, key = { it.id }) { s ->
                    val sVisits = grouped[s.id].orEmpty()
                    val gain = sVisits.sumOf { it.totalGainAmount ?: 0.0 }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                Text("${sVisits.size} visit record(s) • ${s.address}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(formatInr(gain), style = MaterialTheme.typography.titleMedium, color = StatusSuccess)
                        }
                    }
                }
            }
            3 -> {
                items(filteredTasks, key = { it.id }) { task ->
                    val (fg, bg) = statusColors(task.status.name)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.titleSmall)
                                Text("${task.storeName} • ${task.visitorName} • ${task.dueDate}", style = MaterialTheme.typography.bodySmall)
                                if (task.remark.isNotBlank()) Text("Remark: ${task.remark}", style = MaterialTheme.typography.bodySmall)
                            }
                            StatusPill(text = task.status.label, color = fg, backgroundColor = bg)
                        }
                    }
                }
            }
            4 -> {
                items(filteredAttendance, key = { it.id }) { rec ->
                    val (fg, bg) = statusColors(rec.status.name)
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${rec.visitorName} • ${rec.date}", style = MaterialTheme.typography.titleSmall)
                                Text("Marked by ${rec.markedByName}", style = MaterialTheme.typography.bodySmall)
                            }
                            StatusPill(text = rec.status.label, color = fg, backgroundColor = bg)
                        }
                    }
                }
            }
        }
    }
}
