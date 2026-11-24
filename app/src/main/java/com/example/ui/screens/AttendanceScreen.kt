package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AttendanceEntity
import com.example.data.local.AttendanceStatus
import com.example.data.local.RolePermissionEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatDateTime
import com.example.ui.components.statusColors
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun AttendanceScreen(
    user: UserEntity,
    permissions: RolePermissionEntity,
    users: List<UserEntity>,
    attendanceRecords: List<AttendanceEntity>,
    todayDate: String,
    labels: LocalizedLabels,
    onMarkAttendance: (visitor: UserEntity, date: String, status: AttendanceStatus, notes: String) -> Unit
) {
    var selectedDate by rememberSaveable { mutableStateOf(todayDate) }
    var noteText by rememberSaveable { mutableStateOf("") }

    val canMark = permissions.canMarkAttendance || user.role == UserRole.MAIN_SUPER_ADMIN
    val visitors = remember(users) { users.filter { it.role == UserRole.VISITOR && it.isActive } }
    val visibleHistory = remember(attendanceRecords, user) {
        if (user.role == UserRole.VISITOR) attendanceRecords.filter { it.visitorId == user.id }
        else attendanceRecords
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(labels.attendance, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Manual daily attendance (Present / Absent / Late) — separate from store check-in/check-out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (canMark) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Mark Visitor Attendance", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = selectedDate,
                                onValueChange = { selectedDate = it },
                                label = { Text("Date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                label = { Text("Note (Optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f)
                            )
                        }

                        if (visitors.isEmpty()) {
                            Text(
                                "No active visitors registered yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            visitors.forEach { visitor ->
                                val existing = attendanceRecords.firstOrNull {
                                    it.visitorId == visitor.id && it.date == selectedDate
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(visitor.name, style = MaterialTheme.typography.titleSmall)
                                                Text(visitor.mobileNumber, style = MaterialTheme.typography.labelSmall)
                                            }
                                            if (existing != null) {
                                                val (fg, bg) = statusColors(existing.status.name)
                                                StatusPill(text = existing.status.label, color = fg, backgroundColor = bg)
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = { onMarkAttendance(visitor, selectedDate, AttendanceStatus.PRESENT, noteText) },
                                                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .testTag("att_present_${visitor.id}")
                                            ) {
                                                Text(labels.present, maxLines = 1, color = Color.White)
                                            }
                                            Button(
                                                onClick = { onMarkAttendance(visitor, selectedDate, AttendanceStatus.LATE, noteText) },
                                                colors = ButtonDefaults.buttonColors(containerColor = StatusWarning),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .testTag("att_late_${visitor.id}")
                                            ) {
                                                Text(labels.late, maxLines = 1, color = Color.White)
                                            }
                                            Button(
                                                onClick = { onMarkAttendance(visitor, selectedDate, AttendanceStatus.ABSENT, noteText) },
                                                colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .testTag("att_absent_${visitor.id}")
                                            ) {
                                                Text(labels.absent, maxLines = 1, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Attendance Log (${visibleHistory.size})", style = MaterialTheme.typography.titleMedium)
        }

        if (visibleHistory.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.EventAvailable,
                    title = "No Attendance Records",
                    message = "Supervisor attendance marks (Present, Absent, Late) appear here."
                )
            }
        } else {
            items(visibleHistory, key = { it.id }) { record ->
                val (fg, bg) = statusColors(record.status.name)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${record.visitorName} • ${record.date}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Marked by ${record.markedByName} at ${formatDateTime(record.markedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (record.notes.isNotBlank()) {
                                Text("Note: ${record.notes}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        StatusPill(text = record.status.label, color = fg, backgroundColor = bg)
                    }
                }
            }
        }
    }
}
