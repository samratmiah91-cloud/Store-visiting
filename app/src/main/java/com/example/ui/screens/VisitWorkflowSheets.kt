package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.local.AppSettingsEntity
import com.example.data.local.RequestType
import com.example.data.local.StoreAssignmentEntity
import com.example.data.local.StoreEntity
import com.example.data.local.UserEntity
import com.example.service.LocationService
import com.example.service.LocationState
import com.example.service.PhotoStorageHelper
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInSheet(
    store: StoreEntity,
    assignment: StoreAssignmentEntity,
    visitor: UserEntity,
    locationState: LocationState,
    settings: AppSettingsEntity,
    labels: LocalizedLabels,
    photoStorageHelper: PhotoStorageHelper,
    onRequestLocationPermission: () -> Unit,
    onSubmitCheckIn: (
        lat: Double,
        lng: Double,
        photoUri: String,
        daySiName: String,
        daySiMobile: String,
        nightSiName: String,
        nightSiMobile: String,
        dhmName: String,
        dhmMobile: String,
        clmName: String,
        clmMobile: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var capturedPhotoUri by rememberSaveable { mutableStateOf("") }

    var daySiName by rememberSaveable { mutableStateOf(visitor.daySiName) }
    var daySiMobile by rememberSaveable { mutableStateOf(visitor.daySiMobile) }
    var nightSiName by rememberSaveable { mutableStateOf(visitor.nightSiName) }
    var nightSiMobile by rememberSaveable { mutableStateOf(visitor.nightSiMobile) }
    var dhmName by rememberSaveable { mutableStateOf(visitor.dhmName) }
    var dhmMobile by rememberSaveable { mutableStateOf(visitor.dhmMobile) }
    var clmName by rememberSaveable { mutableStateOf(visitor.clmName) }
    var clmMobile by rememberSaveable { mutableStateOf(visitor.clmMobile) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCaptureUri != null) {
            capturedPhotoUri = pendingCaptureUri.toString()
            localError = null
        } else {
            localError = "Camera capture cancelled. Photo is required for check-in."
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, _) = photoStorageHelper.createCameraCaptureUri("checkin_photos")
            pendingCaptureUri = uri
            cameraLauncher.launch(uri)
        } else {
            localError = "Camera permission is required to capture the check-in photo."
        }
    }

    val availableLocation = locationState as? LocationState.Available
    val distanceMeters = availableLocation?.let {
        LocationService.distanceMeters(it.latitude, it.longitude, store.latitude, store.longitude)
    }
    val withinRadius = distanceMeters != null && distanceMeters <= settings.checkInRadiusMeters

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${labels.checkIn} • ${store.name}",
                style = MaterialTheme.typography.headlineSmall
            )

            // GPS Radius Verification Banner
            when (locationState) {
                is LocationState.PermissionRequired -> {
                    Surface(
                        color = StatusErrorBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Location permission is required for GPS check-in.", color = StatusError, style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = onRequestLocationPermission) {
                                Text("Grant GPS Permission")
                            }
                        }
                    }
                }
                is LocationState.Unavailable -> {
                    Surface(
                        color = StatusErrorBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = StatusError)
                            Text("GPS Unavailable: ${locationState.reason}", color = StatusError, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is LocationState.Searching -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Acquiring live GPS fix… Check-in unlocks within ${settings.checkInRadiusMeters} m.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is LocationState.Available -> {
                    val bg = if (withinRadius) StatusSuccessBg else StatusErrorBg
                    val fg = if (withinRadius) StatusSuccess else StatusError
                    Surface(
                        color = bg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (withinRadius) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = fg
                            )
                            Column {
                                Text(
                                    text = if (withinRadius) {
                                        "GPS Verified (${distanceMeters?.toInt()} m from store — within ${settings.checkInRadiusMeters} m)"
                                    } else {
                                        "Check-in Blocked: ${distanceMeters?.toInt()} m away (limit ${settings.checkInRadiusMeters} m)"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = fg
                                )
                                Text(
                                    text = "Live GPS: ${String.format("%.5f", locationState.latitude)}, ${String.format("%.5f", locationState.longitude)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fg
                                )
                            }
                        }
                    }
                }
            }

            // Auto-filled Store & Visitor
            OutlinedTextField(
                value = store.name,
                onValueChange = {},
                readOnly = true,
                label = { Text(labels.storeName) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = visitor.name,
                onValueChange = {},
                readOnly = true,
                label = { Text(labels.visitorName) },
                modifier = Modifier.fillMaxWidth()
            )

            // Mandatory Check-in Photo
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Check-In Photo (Mandatory)", style = MaterialTheme.typography.titleSmall)
                    if (capturedPhotoUri.isNotBlank()) {
                        AsyncImage(
                            model = Uri.parse(capturedPhotoUri),
                            contentDescription = "Captured check-in photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                val (uri, _) = photoStorageHelper.createCameraCaptureUri("checkin_photos")
                                pendingCaptureUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("capture_checkin_photo_button")
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (capturedPhotoUri.isBlank()) "Capture Storefront Photo" else "Retake Photo")
                    }
                }
            }

            Text(
                text = "Store Personnel Details (Auto-saved for future visits)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            PersonnelPairFields("Day SI", daySiName, { daySiName = it }, daySiMobile, { daySiMobile = it })
            PersonnelPairFields("Night SI", nightSiName, { nightSiName = it }, nightSiMobile, { nightSiMobile = it })
            PersonnelPairFields("DHM", dhmName, { dhmName = it }, dhmMobile, { dhmMobile = it })
            PersonnelPairFields("CLM", clmName, { clmName = it }, clmMobile, { clmMobile = it })

            localError?.let {
                Text(it, color = StatusError, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (availableLocation == null) {
                            localError = "GPS location is unavailable."
                            return@Button
                        }
                        if (!withinRadius) {
                            localError = "You must be within ${settings.checkInRadiusMeters} m of the store."
                            return@Button
                        }
                        if (capturedPhotoUri.isBlank()) {
                            localError = "Please capture a check-in photo first."
                            return@Button
                        }
                        if (daySiName.isBlank() || daySiMobile.length < 10 ||
                            nightSiName.isBlank() || nightSiMobile.length < 10 ||
                            dhmName.isBlank() || dhmMobile.length < 10 ||
                            clmName.isBlank() || clmMobile.length < 10
                        ) {
                            localError = "Enter full name and 10-digit mobile for Day SI, Night SI, DHM, and CLM."
                            return@Button
                        }
                        onSubmitCheckIn(
                            availableLocation.latitude,
                            availableLocation.longitude,
                            capturedPhotoUri,
                            daySiName, daySiMobile,
                            nightSiName, nightSiMobile,
                            dhmName, dhmMobile,
                            clmName, clmMobile
                        )
                    },
                    enabled = withinRadius,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("submit_checkin_button")
                ) {
                    Text(labels.checkIn)
                }
            }
        }
    }
}

@Composable
private fun PersonnelPairFields(
    roleLabel: String,
    name: String,
    onNameChange: (String) -> Unit,
    mobile: String,
    onMobileChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("$roleLabel Name") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = mobile,
            onValueChange = { input -> onMobileChange(input.filter { it.isDigit() }.take(10)) },
            label = { Text("$roleLabel Mobile") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CheckOutDialog(
    storeName: String,
    visitorName: String,
    labels: LocalizedLabels,
    onConfirmCheckOut: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var gainText by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${labels.checkOut} • $storeName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "No GPS or photo required at check-out. Report is submitted automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = storeName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(labels.storeName) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = visitorName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(labels.visitorName) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = gainText,
                    onValueChange = { gainText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text(labels.totalGain) },
                    prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("checkout_gain_input")
                )
                errorText?.let {
                    Text(it, color = StatusError, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = gainText.toDoubleOrNull()
                    if (amount == null || amount < 0.0) {
                        errorText = "Enter a valid Total Gain Amount (₹)."
                    } else {
                        onConfirmCheckOut(amount)
                    }
                },
                modifier = Modifier.testTag("confirm_checkout_button")
            ) {
                Text(labels.submitReport)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RescheduleRequestDialog(
    storeName: String,
    defaultDate: String,
    defaultTime: String,
    onSubmit: (RequestType, String?, String?, String) -> Unit,
    onDismiss: () -> Unit
) {
    var requestType by rememberSaveable { mutableStateOf(RequestType.RESCHEDULE) }
    var newDate by rememberSaveable { mutableStateOf(defaultDate) }
    var newTime by rememberSaveable { mutableStateOf(defaultTime) }
    var reason by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel / Reschedule • $storeName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RequestType.entries.forEach { type ->
                        FilterChip(
                            selected = requestType == type,
                            onClick = { requestType = type },
                            label = { Text(type.label) }
                        )
                    }
                }
                if (requestType == RequestType.RESCHEDULE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newDate,
                            onValueChange = { newDate = it },
                            label = { Text("New Date (YYYY-MM-DD)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newTime,
                            onValueChange = { newTime = it },
                            label = { Text("Time (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(0.75f)
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (Mandatory)") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reschedule_reason_input")
                )
                Text(
                    text = "Assignment updates only after Supervisor or Manager approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                errorText?.let { Text(it, color = StatusError, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.trim().length < 3) {
                        errorText = "Please enter a clear reason."
                        return@Button
                    }
                    onSubmit(
                        requestType,
                        if (requestType == RequestType.RESCHEDULE) newDate else null,
                        if (requestType == RequestType.RESCHEDULE) newTime else null,
                        reason
                    )
                },
                modifier = Modifier.testTag("submit_reschedule_button")
            ) {
                Text("Send Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
