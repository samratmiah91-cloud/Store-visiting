package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TaskAlt
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.local.RolePermissionEntity
import com.example.data.local.StoreEntity
import com.example.data.local.SyncStatus
import com.example.data.local.TaskEntity
import com.example.data.local.TaskStatus
import com.example.data.local.UserEntity
import com.example.data.local.UserRole
import com.example.service.PhotoStorageHelper
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatDateTime
import com.example.ui.components.statusColors
import com.example.ui.i18n.LocalizedLabels

@Composable
fun TasksScreen(
    user: UserEntity,
    permissions: RolePermissionEntity,
    tasks: List<TaskEntity>,
    stores: List<StoreEntity>,
    users: List<UserEntity>,
    todayDate: String,
    labels: LocalizedLabels,
    photoStorageHelper: PhotoStorageHelper,
    onCreateTask: (storeId: String, visitorId: String, title: String, desc: String, dueDate: String) -> Unit,
    onUpdateTask: (TaskEntity, Boolean, String, String?) -> Unit
) {
    var filterStatus by rememberSaveable { mutableStateOf<TaskStatus?>(null) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }

    val visibleTasks = remember(tasks, user, filterStatus) {
        tasks.filter { task ->
            (user.role != UserRole.VISITOR || task.visitorId == user.id) &&
                (filterStatus == null || task.status == filterStatus)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(labels.tasks, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${visibleTasks.count { it.status == TaskStatus.COMPLETED }} completed • ${visibleTasks.count { it.status == TaskStatus.PENDING }} pending",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (permissions.canCreateTasks || user.role == UserRole.MAIN_SUPER_ADMIN) {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("create_task_button")
                    ) {
                        Icon(Icons.Filled.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Task")
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filterStatus == null,
                    onClick = { filterStatus = null },
                    label = { Text("All (${tasks.size})") }
                )
                FilterChip(
                    selected = filterStatus == TaskStatus.PENDING,
                    onClick = { filterStatus = TaskStatus.PENDING },
                    label = { Text(labels.pending) }
                )
                FilterChip(
                    selected = filterStatus == TaskStatus.COMPLETED,
                    onClick = { filterStatus = TaskStatus.COMPLETED },
                    label = { Text(labels.completed) }
                )
            }
        }

        if (visibleTasks.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.TaskAlt,
                    title = "No Tasks Matching Filter",
                    message = "Tasks assigned by Admin, Manager, or Supervisor appear here with remarks, optional photos, and completion history."
                )
            }
        } else {
            items(visibleTasks, key = { it.id }) { task ->
                val (fg, bg) = statusColors(task.status.name)
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
                                Text(task.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${task.storeName} • Assigned to ${task.visitorName} • Due ${task.dueDate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill(text = task.status.label, color = fg, backgroundColor = bg)
                        }

                        if (task.description.isNotBlank()) {
                            Text(task.description, style = MaterialTheme.typography.bodyMedium)
                        }

                        if (task.remark.isNotBlank()) {
                            Text(
                                text = "Remark: ${task.remark}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (!task.photoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = Uri.parse(task.photoUri),
                                contentDescription = "Task evidence photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "By ${task.createdByName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (task.completedAt != null) {
                                    Text(
                                        text = "Completed: ${formatDateTime(task.completedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (task.syncStatus == SyncStatus.PENDING_SYNC) {
                                    Text(
                                        text = "Pending Sync",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { editingTask = task },
                                modifier = Modifier.testTag("update_task_${task.id}")
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (task.status == TaskStatus.COMPLETED) "Edit / Reopen" else "Complete / Remark")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTaskDialog(
            stores = stores,
            visitors = users.filter { it.role == UserRole.VISITOR && it.isActive },
            defaultDate = todayDate,
            onCreate = { storeId, visitorId, title, desc, due ->
                onCreateTask(storeId, visitorId, title, desc, due)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    editingTask?.let { task ->
        UpdateTaskDialog(
            task = task,
            photoStorageHelper = photoStorageHelper,
            onSave = { completed, remark, photo ->
                onUpdateTask(task, completed, remark, photo)
                editingTask = null
            },
            onDismiss = { editingTask = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskDialog(
    stores: List<StoreEntity>,
    visitors: List<UserEntity>,
    defaultDate: String,
    onCreate: (String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStore by remember { mutableStateOf(stores.firstOrNull()) }
    var selectedVisitor by remember { mutableStateOf(visitors.firstOrNull()) }
    var storeMenuExpanded by remember { mutableStateOf(false) }
    var visitorMenuExpanded by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf(defaultDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create & Assign Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(
                    expanded = storeMenuExpanded,
                    onExpandedChange = { storeMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedStore?.name ?: "No stores available",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Store") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = storeMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = storeMenuExpanded, onDismissRequest = { storeMenuExpanded = false }) {
                        stores.forEach { store ->
                            DropdownMenuItem(
                                text = { Text(store.name) },
                                onClick = {
                                    selectedStore = store
                                    storeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = visitorMenuExpanded,
                    onExpandedChange = { visitorMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedVisitor?.name ?: "No visitors registered",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Visitor") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = visitorMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = visitorMenuExpanded, onDismissRequest = { visitorMenuExpanded = false }) {
                        visitors.forEach { v ->
                            DropdownMenuItem(
                                text = { Text("${v.name} (${v.mobileNumber})") },
                                onClick = {
                                    selectedVisitor = v
                                    visitorMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Instructions") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = selectedStore ?: return@Button
                    val v = selectedVisitor ?: return@Button
                    onCreate(s.id, v.id, title, desc, dueDate)
                },
                enabled = selectedStore != null && selectedVisitor != null && title.isNotBlank()
            ) {
                Text("Assign Task")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun UpdateTaskDialog(
    task: TaskEntity,
    photoStorageHelper: PhotoStorageHelper,
    onSave: (Boolean, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isCompleted by rememberSaveable { mutableStateOf(task.status == TaskStatus.COMPLETED) }
    var remark by rememberSaveable { mutableStateOf(task.remark) }
    var photoUri by rememberSaveable { mutableStateOf(task.photoUri) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && pendingCaptureUri != null) {
            photoUri = pendingCaptureUri.toString()
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (uri, _) = photoStorageHelper.createCameraCaptureUri("task_photos")
            pendingCaptureUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Zero-permission Android Photo Picker
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            photoUri = photoStorageHelper.copyPickerUriToInternal(uri, "task_photos") ?: uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isCompleted,
                        onClick = { isCompleted = false },
                        label = { Text("Pending") }
                    )
                    FilterChip(
                        selected = isCompleted,
                        onClick = { isCompleted = true },
                        label = { Text("Complete") }
                    )
                }
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("Remark") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Optional Photo", style = MaterialTheme.typography.labelMedium)
                if (!photoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = Uri.parse(photoUri),
                        contentDescription = "Task photo preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                val (uri, _) = photoStorageHelper.createCameraCaptureUri("task_photos")
                                pendingCaptureUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Camera")
                    }
                    OutlinedButton(
                        onClick = {
                            pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Gallery")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(isCompleted, remark, photoUri) }) {
                Text("Save Task")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
