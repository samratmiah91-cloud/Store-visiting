package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.ActivityLogEntity
import com.example.data.local.AppSettingsEntity
import com.example.data.local.ApprovalStatus
import com.example.data.local.PermissionKey
import com.example.data.local.RescheduleRequestEntity
import com.example.data.local.RolePermissionEntity
import com.example.data.local.StoreEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserRole
import com.example.service.LocationState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoleBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.formatDateTime
import com.example.ui.components.statusColors
import kotlinx.coroutines.launch

@Composable
fun AdminControlScreen(
    currentUser: UserEntity,
    permissions: RolePermissionEntity,
    users: List<UserEntity>,
    rolePermissions: List<RolePermissionEntity>,
    stores: List<StoreEntity>,
    rescheduleRequests: List<RescheduleRequestEntity>,
    activityLogs: List<ActivityLogEntity>,
    appSettings: AppSettingsEntity,
    offlineSimulationEnabled: Boolean,
    todayDate: String,
    onSaveUser: (String?, String, String, UserRole, Boolean, Boolean) -> Unit,
    onTogglePermission: (UserRole, PermissionKey, Boolean) -> Unit,
    onSaveStore: (String?, String, String, Double, Double, String, String) -> Unit,
    onFetchFreshLocation: suspend () -> LocationState,
    onAssignStore: (String, String, String, String, String) -> Unit,
    onReviewReschedule: (RescheduleRequestEntity, Boolean, String) -> Unit,
    onUpdateSettings: (Int, Int, Int) -> Unit,
    onSendAnnouncement: (String, String) -> Unit,
    onToggleOfflineSim: (Boolean) -> Unit,
    onCreateBackup: (Uri) -> Unit,
    onRestoreBackup: (Uri) -> Unit
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var userSearch by rememberSaveable { mutableStateOf("") }
    var storeSearch by rememberSaveable { mutableStateOf("") }
    var editingUser by remember { mutableStateOf<UserEntity?>(null) }
    var showCreateUserDialog by rememberSaveable { mutableStateOf(false) }
    var editingStore by remember { mutableStateOf<StoreEntity?>(null) }
    var showCreateStoreDialog by rememberSaveable { mutableStateOf(false) }
    var showAssignDialog by rememberSaveable { mutableStateOf(false) }

    val sections = buildList {
        if (currentUser.role == UserRole.MAIN_SUPER_ADMIN || permissions.canManageUsers) add("Users" to 0)
        if (currentUser.role == UserRole.MAIN_SUPER_ADMIN) add("Permissions" to 1)
        if (currentUser.role == UserRole.MAIN_SUPER_ADMIN || permissions.canManageStores || permissions.canAssignStores) add("Stores" to 2)
        if (currentUser.role == UserRole.MAIN_SUPER_ADMIN || permissions.canApproveReschedule) add("Approvals" to 3)
        if (currentUser.role == UserRole.MAIN_SUPER_ADMIN || permissions.canConfigureSettings || permissions.canSendAnnouncements) add("System" to 4)
    }

    val effectiveSection = if (sections.any { it.second == selectedSection }) selectedSection else (sections.firstOrNull()?.second ?: 0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Operations & Governance", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sections.forEach { (label, id) ->
                    FilterChip(
                        selected = effectiveSection == id,
                        onClick = { selectedSection = id },
                        label = { Text(label) }
                    )
                }
            }
        }

        when (effectiveSection) {
            0 -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = userSearch,
                            onValueChange = { userSearch = it },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text("Search users") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { showCreateUserDialog = true },
                            modifier = Modifier.height(52.dp).testTag("add_user_button")
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null)
                        }
                    }
                }
                val filteredUsers = users.filter {
                    userSearch.isBlank() || it.name.contains(userSearch, true) || it.mobileNumber.contains(userSearch)
                }
                items(filteredUsers, key = { it.id }) { targetUser ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(targetUser.name, style = MaterialTheme.typography.titleMedium)
                                    RoleBadge(targetUser.role)
                                }
                                Text(targetUser.mobileNumber, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = "${if (targetUser.isApproved) "Approved" else "Pending Approval"} • ${if (targetUser.isActive) "Active" else "Inactive"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (currentUser.role == UserRole.MAIN_SUPER_ADMIN || targetUser.role.hierarchyRank <= currentUser.role.hierarchyRank && targetUser.role != UserRole.MAIN_SUPER_ADMIN) {
                                IconButton(onClick = { editingUser = targetUser }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit User")
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                item {
                    PermissionMatrixPanel(
                        rolePermissions = rolePermissions,
                        onTogglePermission = onTogglePermission
                    )
                }
            }
            2 -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentUser.role == UserRole.MAIN_SUPER_ADMIN || permissions.canManageStores) {
                            Button(
                                onClick = { showCreateStoreDialog = true },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("add_store_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add Store")
                            }
                        }
                        if (currentUser.role == UserRole.MAIN_SUPER_ADMIN || permissions.canAssignStores) {
                            OutlinedButton(
                                onClick = { showAssignDialog = true },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("assign_store_button")
                            ) {
                                Icon(Icons.Filled.AssignmentInd, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Assign Store")
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = storeSearch,
                        onValueChange = { storeSearch = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("Search stores") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val filteredStores = stores.filter {
                    storeSearch.isBlank() || it.name.contains(storeSearch, true) || it.address.contains(storeSearch, true)
                }
                if (filteredStores.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Storefront,
                            title = "No Stores Added",
                            message = "Add store locations with GPS coordinates and assign them to visitors without any daily cap."
                        )
                    }
                } else {
                    items(filteredStores, key = { it.id }) { store ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(store.name, style = MaterialTheme.typography.titleMedium)
                                    Text(store.address, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "GPS: ${String.format("%.5f", store.latitude)}, ${String.format("%.5f", store.longitude)} • ${store.contactName} (${store.contactPhone})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (currentUser.role == UserRole.MAIN_SUPER_ADMIN || permissions.canManageStores) {
                                    IconButton(onClick = { editingStore = store }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit Store")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            3 -> {
                if (rescheduleRequests.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Lock,
                            title = "No Cancel / Reschedule Requests",
                            message = "Visitor requests appear here for Supervisor or Manager approval."
                        )
                    }
                } else {
                    items(rescheduleRequests, key = { it.id }) { req ->
                        val (fg, bg) = statusColors(req.status.name)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${req.requestType.label}: ${req.storeName}", style = MaterialTheme.typography.titleSmall)
                                        Text("Visitor: ${req.visitorName}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    StatusPill(text = req.status.label, color = fg, backgroundColor = bg)
                                }
                                if (!req.requestedDate.isNullOrBlank()) {
                                    Text("Requested Slot: ${req.requestedDate} ${req.requestedTime ?: ""}", style = MaterialTheme.typography.labelSmall)
                                }
                                Text("Reason: ${req.reason}", style = MaterialTheme.typography.bodySmall)
                                if (req.status == ApprovalStatus.PENDING) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onReviewReschedule(req, true, "Approved") },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Approve")
                                        }
                                        OutlinedButton(
                                            onClick = { onReviewReschedule(req, false, "Declined") },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Reject")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            4 -> {
                item {
                    SystemSettingsAndBackupPanel(
                        currentUser = currentUser,
                        permissions = permissions,
                        appSettings = appSettings,
                        offlineSimulationEnabled = offlineSimulationEnabled,
                        activityLogs = activityLogs,
                        onUpdateSettings = onUpdateSettings,
                        onSendAnnouncement = onSendAnnouncement,
                        onToggleOfflineSim = onToggleOfflineSim,
                        onCreateBackup = onCreateBackup,
                        onRestoreBackup = onRestoreBackup
                    )
                }
            }
        }
    }

    if (showCreateUserDialog || editingUser != null) {
        UserEditorDialog(
            actorRole = currentUser.role,
            existing = editingUser,
            onSave = { name, mobile, role, approved, active ->
                onSaveUser(editingUser?.id, name, mobile, role, approved, active)
                showCreateUserDialog = false
                editingUser = null
            },
            onDismiss = {
                showCreateUserDialog = false
                editingUser = null
            }
        )
    }

    if (showCreateStoreDialog || editingStore != null) {
        StoreEditorDialog(
            existing = editingStore,
            onFetchFreshLocation = onFetchFreshLocation,
            onSave = { name, addr, lat, lng, cName, cPhone ->
                onSaveStore(editingStore?.id, name, addr, lat, lng, cName, cPhone)
                showCreateStoreDialog = false
                editingStore = null
            },
            onDismiss = {
                showCreateStoreDialog = false
                editingStore = null
            }
        )
    }

    if (showAssignDialog) {
        AssignStoreDialog(
            stores = stores,
            visitors = users.filter { it.role == UserRole.VISITOR && it.isActive },
            defaultDate = todayDate,
            onAssign = { storeId, visitorId, date, time, notes ->
                onAssignStore(storeId, visitorId, date, time, notes)
                showAssignDialog = false
            },
            onDismiss = { showAssignDialog = false }
        )
    }
}
