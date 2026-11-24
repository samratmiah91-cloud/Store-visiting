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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.ApprovalStatus
import com.example.data.local.AssignmentStatus
import com.example.data.local.AttendanceEntity
import com.example.data.local.RescheduleRequestEntity
import com.example.data.local.RolePermissionEntity
import com.example.data.local.StoreAssignmentEntity
import com.example.data.local.StoreEntity
import com.example.data.local.TaskEntity
import com.example.data.local.TaskStatus
import com.example.data.local.UserEntity
import com.example.data.local.UserRole
import com.example.data.local.VisitRecordEntity
import com.example.service.LocationService
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatInr
import com.example.ui.components.statusColors
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TealPrimary
import com.example.ui.viewmodel.StoreWithDistance

@Composable
fun HomeDashboardScreen(
    user: UserEntity,
    permissions: RolePermissionEntity,
    users: List<UserEntity>,
    stores: List<StoreEntity>,
    assignments: List<StoreAssignmentEntity>,
    visits: List<VisitRecordEntity>,
    tasks: List<TaskEntity>,
    attendance: List<AttendanceEntity>,
    rescheduleRequests: List<RescheduleRequestEntity>,
    visitorStoresWithDistance: List<StoreWithDistance>,
    labels: LocalizedLabels,
    todayDate: String,
    onNavigateTab: (String) -> Unit,
    onCheckInClick: (StoreEntity, StoreAssignmentEntity) -> Unit,
    onCheckOutClick: (StoreEntity, StoreAssignmentEntity) -> Unit,
    onLoadSampleData: () -> Unit
) {
    val totalGain = visits.sumOf { it.totalGainAmount ?: 0.0 }
    val completedVisits = visits.count { it.reportSubmitted }
    val pendingAssignments = assignments.count { it.status == AssignmentStatus.PENDING || it.status == AssignmentStatus.LATE || it.status == AssignmentStatus.RESCHEDULED }
    val lateOrMissed = assignments.count { it.status == AssignmentStatus.LATE || it.status == AssignmentStatus.MISSED }
    val pendingApprovals = rescheduleRequests.count { it.status == ApprovalStatus.PENDING }
    val pendingTasks = tasks.count {
        it.status == TaskStatus.PENDING && (user.role != UserRole.VISITOR || it.visitorId == user.id)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = when (user.role) {
                            UserRole.MAIN_SUPER_ADMIN -> "Main Super Admin Command Center"
                            UserRole.ADMIN -> "Admin Operations Dashboard"
                            UserRole.MANAGER -> "Manager Territory Dashboard"
                            UserRole.SUPERVISOR -> "Supervisor Field Desk"
                            UserRole.VISITOR -> "Visitor Daily Route • $todayDate"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = when (user.role) {
                            UserRole.MAIN_SUPER_ADMIN -> "Full governance over users, role permissions, stores, reports, and system backups."
                            UserRole.ADMIN -> "Store operations, assignments, and reporting within Main Super Admin permissions."
                            UserRole.MANAGER -> "Monitor daily store visits, approve reschedules, and track team gain."
                            UserRole.SUPERVISOR -> "Mark daily attendance, assign field tasks, and review reschedule requests."
                            UserRole.VISITOR -> "Follow live store distances, verify 100m GPS + photo check-in, and submit checkout reports."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (user.role == UserRole.MAIN_SUPER_ADMIN && stores.isEmpty() && users.size <= 1) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Quick Workspace Bootstrap", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Add your team & stores manually in Admin, or load a ready-to-test sample network (Admin, Manager, Supervisor, Visitor & 3 stores).",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = onLoadSampleData,
                            modifier = Modifier.testTag("load_sample_data_button")
                        ) {
                            Text("Load Sample Stores & Team")
                        }
                    }
                }
            }
        }

        // Role-specific KPI Grid
        item {
            if (user.role == UserRole.VISITOR) {
                val myAssigned = visitorStoresWithDistance.size
                val myCompleted = visitorStoresWithDistance.count { it.assignment.status == AssignmentStatus.COMPLETED }
                val myGain = visits.filter { it.visitorId == user.id }.sumOf { it.totalGainAmount ?: 0.0 }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        KpiMetricCard(
                            title = "Assigned Stores",
                            value = myAssigned.toString(),
                            icon = Icons.Filled.Storefront,
                            accentColor = TealPrimary,
                            subtitle = "$myCompleted completed",
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Pending Tasks",
                            value = pendingTasks.toString(),
                            icon = Icons.Filled.AssignmentTurnedIn,
                            accentColor = AmberAccent,
                            subtitle = "Tap Tasks tab",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    KpiMetricCard(
                        title = "Total Gain Submitted",
                        value = formatInr(myGain),
                        icon = Icons.Filled.CurrencyRupee,
                        accentColor = StatusSuccess,
                        subtitle = "Auto-submitted on check-out",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        KpiMetricCard(
                            title = "Total Gain",
                            value = formatInr(totalGain),
                            icon = Icons.Filled.CurrencyRupee,
                            accentColor = StatusSuccess,
                            subtitle = "$completedVisits reports submitted",
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Pending Visits",
                            value = pendingAssignments.toString(),
                            icon = Icons.Filled.PendingActions,
                            accentColor = StatusInfo,
                            subtitle = "$lateOrMissed late / missed",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        KpiMetricCard(
                            title = if (user.role == UserRole.MAIN_SUPER_ADMIN || user.role == UserRole.ADMIN) "Team & Stores" else "Pending Approvals",
                            value = if (user.role == UserRole.MAIN_SUPER_ADMIN || user.role == UserRole.ADMIN) {
                                "${users.size} / ${stores.size}"
                            } else {
                                pendingApprovals.toString()
                            },
                            icon = if (user.role == UserRole.MAIN_SUPER_ADMIN || user.role == UserRole.ADMIN) Icons.Filled.Group else Icons.Filled.WarningAmber,
                            accentColor = TealPrimary,
                            subtitle = if (user.role == UserRole.MAIN_SUPER_ADMIN || user.role == UserRole.ADMIN) "$pendingApprovals reschedule requests" else "Cancel / Reschedule",
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "Attendance Today",
                            value = attendance.count { it.date == todayDate }.toString(),
                            icon = Icons.Filled.EventAvailable,
                            accentColor = AmberAccent,
                            subtitle = "$pendingTasks open tasks",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Quick Actions (Respecting live permissions)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (permissions.canViewLiveMap) {
                    OutlinedButton(
                        onClick = { onNavigateTab("map") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(labels.liveMap, maxLines = 1)
                    }
                }
                if (permissions.canViewReports) {
                    OutlinedButton(
                        onClick = { onNavigateTab("reports") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(labels.reports, maxLines = 1)
                    }
                }
                if (user.role == UserRole.MAIN_SUPER_ADMIN || permissions.canManageUsers || permissions.canManageStores || permissions.canAssignStores || permissions.canApproveReschedule) {
                    OutlinedButton(
                        onClick = { onNavigateTab("admin") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(labels.governance, maxLines = 1)
                    }
                }
            }
        }

        // Visitor Assigned Stores Feed or Management Live Feed
        if (user.role == UserRole.VISITOR) {
            item {
                Text("My Assigned Stores", style = MaterialTheme.typography.titleMedium)
            }
            if (visitorStoresWithDistance.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Storefront,
                        title = "No Stores Assigned Yet",
                        message = "When a Manager or Admin assigns stores to you, they will appear here and on the Live Map."
                    )
                }
            } else {
                items(visitorStoresWithDistance, key = { it.assignment.id }) { item ->
                    val (fg, bg) = statusColors(item.assignment.status.name)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${item.markerNumber}. ${item.store.name}", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${item.store.address} • ${item.assignment.assignedDate} ${item.assignment.scheduledTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(text = item.assignment.status.label, color = fg, backgroundColor = bg)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.distanceMeters?.let { "Distance: ${LocationService.formatDistance(it)}" } ?: "Distance: Waiting for GPS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (item.isNearestPending) AmberAccent else MaterialTheme.colorScheme.primary
                                )
                                if (permissions.canCheckInOut) {
                                    when (item.assignment.status) {
                                        AssignmentStatus.PENDING, AssignmentStatus.LATE, AssignmentStatus.RESCHEDULED -> {
                                            Button(onClick = { onCheckInClick(item.store, item.assignment) }) {
                                                Text(labels.checkIn)
                                            }
                                        }
                                        AssignmentStatus.CHECKED_IN -> {
                                            Button(onClick = { onCheckOutClick(item.store, item.assignment) }) {
                                                Text(labels.checkOut)
                                            }
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Text("Recent Visit & Assignment Activity", style = MaterialTheme.typography.titleMedium)
            }
            if (assignments.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Storefront,
                        title = "No Store Assignments Yet",
                        message = "Create stores and assign them to visitors from the Admin / Ops tab."
                    )
                }
            } else {
                val storeMap = stores.associateBy { it.id }
                val userMap = users.associateBy { it.id }
                items(assignments.take(10), key = { it.id }) { asgn ->
                    val storeName = storeMap[asgn.storeId]?.name ?: asgn.storeId
                    val visitorName = userMap[asgn.visitorId]?.name ?: asgn.visitorId
                    val (fg, bg) = statusColors(asgn.status.name)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(storeName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "$visitorName • ${asgn.assignedDate} ${asgn.scheduledTime}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(text = asgn.status.label, color = fg, backgroundColor = bg)
                        }
                    }
                }
            }
        }
    }
}
