package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.local.PermissionKey
import com.example.data.local.RolePermissionEntity
import com.example.data.local.StoreEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserRole
import com.example.service.LocationState
import com.example.ui.components.formatDateTime
import kotlinx.coroutines.launch

@Composable
fun PermissionMatrixPanel(
    rolePermissions: List<RolePermissionEntity>,
    onTogglePermission: (UserRole, PermissionKey, Boolean) -> Unit
) {
    val configurableRoles = listOf(UserRole.ADMIN, UserRole.MANAGER, UserRole.SUPERVISOR, UserRole.VISITOR)
    var selectedRole by rememberSaveable { mutableStateOf(UserRole.ADMIN) }
    val currentConfig = rolePermissions.firstOrNull { it.roleName == selectedRole.name }
        ?: RolePermissionEntity.defaultsFor(selectedRole)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Role Permission Matrix (Takes effect immediately)",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Main Super Admin controls every role individually. Admin cannot exceed these grants.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            configurableRoles.forEach { role ->
                FilterChip(
                    selected = selectedRole == role,
                    onClick = { selectedRole = role },
                    label = { Text(role.displayName) }
                )
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                PermissionKey.entries.forEachIndexed { idx, key ->
                    val enabled = currentConfig.hasPermission(key)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(key.label, style = MaterialTheme.typography.titleSmall)
                            Text(key.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { onTogglePermission(selectedRole, key, it) },
                            modifier = Modifier.testTag("perm_${selectedRole.name}_${key.name}")
                        )
                    }
                    if (idx < PermissionKey.entries.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    }
                }
            }
        }
    }
}

@Composable
fun SystemSettingsAndBackupPanel(
    currentUser: UserEntity,
    permissions: RolePermissionEntity,
    appSettings: AppSettingsEntity,
    offlineSimulationEnabled: Boolean,
    activityLogs: List<ActivityLogEntity>,
    onUpdateSettings: (Int, Int, Int) -> Unit,
    onSendAnnouncement: (String, String) -> Unit,
    onToggleOfflineSim: (Boolean) -> Unit,
    onCreateBackup: (Uri) -> Unit,
    onRestoreBackup: (Uri) -> Unit
) {
    var lateMinText by rememberSaveable(appSettings.lateThresholdMinutes) {
        mutableStateOf(appSettings.lateThresholdMinutes.toString())
    }
    var cutoffHourText by rememberSaveable(appSettings.missedCutoffHour) {
        mutableStateOf(appSettings.missedCutoffHour.toString())
    }
    var radiusText by rememberSaveable(appSettings.checkInRadiusMeters) {
        mutableStateOf(appSettings.checkInRadiusMeters.toString())
    }
    var annTitle by rememberSaveable { mutableStateOf("") }
    var annMessage by rememberSaveable { mutableStateOf("") }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onCreateBackup(uri)
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onRestoreBackup(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (currentUser.role == UserRole.MAIN_SUPER_ADMIN || permissions.canConfigureSettings) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Visit Thresholds & Offline Queue", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lateMinText,
                            onValueChange = { lateMinText = it.filter(Char::isDigit) },
                            label = { Text("Late Threshold (min)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = cutoffHourText,
                            onValueChange = { cutoffHourText = it.filter(Char::isDigit) },
                            label = { Text("Missed Hour (24h)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = radiusText,
                        onValueChange = { radiusText = it.filter(Char::isDigit) },
                        label = { Text("Check-In GPS Radius (metres)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onUpdateSettings(
                                lateMinText.toIntOrNull() ?: 30,
                                cutoffHourText.toIntOrNull() ?: 20,
                                radiusText.toIntOrNull() ?: 100
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Threshold Settings")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Offline Sync Queue Test Mode", style = MaterialTheme.typography.titleSmall)
                            Text("Queue new visits/tasks offline and auto-sync when turned back online.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = offlineSimulationEnabled, onCheckedChange = onToggleOfflineSim)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Database Backup & Restore", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (appSettings.lastBackupAt != null) {
                            "Last backup: ${formatDateTime(appSettings.lastBackupAt)} (${appSettings.backupSummary})"
                        } else {
                            "Export a full encrypted-ready JSON snapshot of users, stores, visits, tasks, and logs."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { backupLauncher.launch("StoreVisit_Backup_${System.currentTimeMillis()}.json") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export Backup")
                        }
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                }
            }
        }

        if (currentUser.role == UserRole.MAIN_SUPER_ADMIN || permissions.canSendAnnouncements) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Admin Announcement", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = annTitle,
                        onValueChange = { annTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = annMessage,
                        onValueChange = { annMessage = it },
                        label = { Text("Message") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSendAnnouncement(annTitle, annMessage)
                            annTitle = ""
                            annMessage = ""
                        },
                        enabled = annTitle.isNotBlank() && annMessage.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Campaign, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Broadcast Announcement")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Security & Activity Audit Log (${activityLogs.size})", style = MaterialTheme.typography.titleMedium)
                }
                activityLogs.take(15).forEach { log ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("${log.action} • ${log.actorName} (${log.actorRole})", style = MaterialTheme.typography.labelMedium)
                        Text("${log.details} • ${formatDateTime(log.timestamp)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        }
    }
}

@Composable
fun UserEditorDialog(
    actorRole: UserRole,
    existing: UserEntity?,
    onSave: (String, String, UserRole, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var mobile by rememberSaveable { mutableStateOf(existing?.mobileNumber ?: "") }
    val assignableRoles = remember(actorRole) {
        if (actorRole == UserRole.MAIN_SUPER_ADMIN) {
            listOf(UserRole.ADMIN, UserRole.MANAGER, UserRole.SUPERVISOR, UserRole.VISITOR)
        } else {
            UserRole.entries.filter { it != UserRole.MAIN_SUPER_ADMIN && it.hierarchyRank <= actorRole.hierarchyRank }
        }
    }
    var selectedRole by rememberSaveable {
        mutableStateOf(existing?.role?.takeIf { it in assignableRoles } ?: UserRole.VISITOR)
    }
    var isApproved by rememberSaveable { mutableStateOf(existing?.isApproved ?: true) }
    var isActive by rememberSaveable { mutableStateOf(existing?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create User" else "Edit User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it.filter(Char::isDigit).take(10) },
                    label = { Text("10-Digit Mobile") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Role", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    assignableRoles.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.displayName) }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Approved for Login")
                    Switch(checked = isApproved, onCheckedChange = { isApproved = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Account Active")
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, mobile, selectedRole, isApproved, isActive) },
                enabled = name.trim().length >= 2 && mobile.length == 10
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun StoreEditorDialog(
    existing: StoreEntity?,
    onFetchFreshLocation: suspend () -> LocationState,
    onSave: (String, String, Double, Double, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var address by rememberSaveable { mutableStateOf(existing?.address ?: "") }
    var latText by rememberSaveable { mutableStateOf(existing?.latitude?.toString() ?: "") }
    var lngText by rememberSaveable { mutableStateOf(existing?.longitude?.toString() ?: "") }
    var contactName by rememberSaveable { mutableStateOf(existing?.contactName ?: "") }
    var contactPhone by rememberSaveable { mutableStateOf(existing?.contactPhone ?: "") }
    var gpsStatusMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Store" else "Edit Store") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Store Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text("Longitude") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            when (val loc = onFetchFreshLocation()) {
                                is LocationState.Available -> {
                                    latText = String.format("%.6f", loc.latitude)
                                    lngText = String.format("%.6f", loc.longitude)
                                    gpsStatusMsg = "Captured current GPS (±${loc.accuracyMeters.toInt()}m)"
                                }
                                is LocationState.Unavailable -> gpsStatusMsg = loc.reason
                                is LocationState.PermissionRequired -> gpsStatusMsg = "Grant GPS permission first."
                                is LocationState.Searching -> gpsStatusMsg = "Searching…"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use My Current GPS")
                }
                gpsStatusMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(value = contactName, onValueChange = { contactName = it }, label = { Text("Contact Person") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it.filter(Char::isDigit).take(10) },
                    label = { Text("Contact Phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latText.toDoubleOrNull() ?: return@Button
                    val lng = lngText.toDoubleOrNull() ?: return@Button
                    onSave(name, address, lat, lng, contactName, contactPhone)
                },
                enabled = name.isNotBlank() && address.isNotBlank() && latText.toDoubleOrNull() != null && lngText.toDoubleOrNull() != null
            ) { Text("Save Store") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignStoreDialog(
    stores: List<StoreEntity>,
    visitors: List<UserEntity>,
    defaultDate: String,
    onAssign: (String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStore by remember { mutableStateOf(stores.firstOrNull()) }
    var selectedVisitor by remember { mutableStateOf(visitors.firstOrNull()) }
    var storeExpanded by remember { mutableStateOf(false) }
    var visitorExpanded by remember { mutableStateOf(false) }
    var date by rememberSaveable { mutableStateOf(defaultDate) }
    var time by rememberSaveable { mutableStateOf("10:00") }
    var notes by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Store to Visitor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("No daily store limit — assign as many stores as required.", style = MaterialTheme.typography.bodySmall)
                ExposedDropdownMenuBox(expanded = storeExpanded, onExpandedChange = { storeExpanded = it }) {
                    OutlinedTextField(
                        value = selectedStore?.name ?: "No stores",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Store") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(storeExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = storeExpanded, onDismissRequest = { storeExpanded = false }) {
                        stores.forEach { s ->
                            DropdownMenuItem(text = { Text(s.name) }, onClick = { selectedStore = s; storeExpanded = false })
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = visitorExpanded, onExpandedChange = { visitorExpanded = it }) {
                    OutlinedTextField(
                        value = selectedVisitor?.name ?: "No active visitors",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Visitor") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(visitorExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = visitorExpanded, onDismissRequest = { visitorExpanded = false }) {
                        visitors.forEach { v ->
                            DropdownMenuItem(text = { Text("${v.name} (${v.mobileNumber})") }, onClick = { selectedVisitor = v; visitorExpanded = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Time (HH:mm)") }, singleLine = true, modifier = Modifier.weight(0.75f))
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Visit Note (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = selectedStore ?: return@Button
                    val v = selectedVisitor ?: return@Button
                    onAssign(s.id, v.id, date, time, notes)
                },
                enabled = selectedStore != null && selectedVisitor != null
            ) { Text("Assign") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
