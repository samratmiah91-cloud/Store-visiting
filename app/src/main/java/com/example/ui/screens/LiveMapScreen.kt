package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettingsEntity
import com.example.data.local.AssignmentStatus
import com.example.data.local.RolePermissionEntity
import com.example.data.local.StoreAssignmentEntity
import com.example.data.local.StoreEntity
import com.example.service.LocationService
import com.example.service.LocationState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.statusColors
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TealPrimary
import com.example.ui.viewmodel.StoreWithDistance
import kotlin.math.cos
import kotlin.math.max

@Composable
fun LiveMapScreen(
    storesWithDistance: List<StoreWithDistance>,
    locationState: LocationState,
    permissions: RolePermissionEntity,
    settings: AppSettingsEntity,
    labels: LocalizedLabels,
    onRequestLocationPermission: () -> Unit,
    onCheckInClick: (StoreEntity, StoreAssignmentEntity) -> Unit,
    onCheckOutClick: (StoreEntity, StoreAssignmentEntity) -> Unit,
    onRescheduleClick: (StoreEntity, StoreAssignmentEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedAssignmentId by remember(storesWithDistance) {
        mutableStateOf(
            storesWithDistance.firstOrNull { it.isNearestPending }?.assignment?.id
                ?: storesWithDistance.firstOrNull()?.assignment?.id
        )
    }

    val sortedList = remember(storesWithDistance) {
        storesWithDistance.sortedWith(
            compareBy<StoreWithDistance>(
                { it.assignment.status == AssignmentStatus.COMPLETED || it.assignment.status == AssignmentStatus.MISSED },
                { it.distanceMeters ?: Double.MAX_VALUE }
            )
        )
    }

    val selectedStoreItem = sortedList.firstOrNull { it.assignment.id == selectedAssignmentId }
        ?: sortedList.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GpsBannerCard(
                locationState = locationState,
                radiusMeters = settings.checkInRadiusMeters,
                onRequestLocationPermission = onRequestLocationPermission
            )
        }

        if (sortedList.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Storefront,
                    title = "No Assigned Stores",
                    message = "Assigned stores appear here with numbered map pins, live distances, and Google Maps navigation."
                )
            }
        } else {
            item {
                InteractiveStoreCanvasMap(
                    stores = sortedList,
                    locationState = locationState,
                    selectedAssignmentId = selectedStoreItem?.assignment?.id,
                    radiusMeters = settings.checkInRadiusMeters,
                    onSelectStore = { selectedAssignmentId = it.assignment.id }
                )
            }

            item {
                Text(
                    text = "Assigned Stores (${sortedList.size}) • Sorted by Live Distance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(sortedList, key = { it.assignment.id }) { item ->
                val isSelected = item.assignment.id == selectedStoreItem?.assignment?.id
                val (fg, bg) = statusColors(item.assignment.status.name)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedAssignmentId = item.assignment.id }
                        .testTag("map_store_card_${item.markerNumber}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (item.isNearestPending) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected || item.isNearestPending) 1.5.dp else 1.dp,
                        color = when {
                            item.isNearestPending -> TealPrimary
                            isSelected -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (item.isNearestPending) AmberAccent else TealPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.markerNumber.toString(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.store.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${item.store.address} • ${item.assignment.assignedDate} ${item.assignment.scheduledTime}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            StatusPill(text = item.assignment.status.label, color = fg, backgroundColor = bg)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (item.isNearestPending) {
                                StatusPill(
                                    text = labels.nearestStore,
                                    color = Color.White,
                                    backgroundColor = AmberAccent
                                )
                            }
                            val distText = item.distanceMeters?.let { LocationService.formatDistance(it) } ?: "Distance pending GPS"
                            Text(
                                text = "Distance: $distText",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val navUri = Uri.parse("google.navigation:q=${item.store.latitude},${item.store.longitude}")
                                    val geoUri = Uri.parse("geo:${item.store.latitude},${item.store.longitude}?q=${item.store.latitude},${item.store.longitude}(${Uri.encode(item.store.name)})")
                                    val mapsIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    try {
                                        context.startActivity(mapsIntent)
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                                        } catch (_: Exception) {
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("google_maps_nav_${item.markerNumber}")
                            ) {
                                Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Maps")
                            }

                            if (permissions.canCheckInOut) {
                                when (item.assignment.status) {
                                    AssignmentStatus.PENDING, AssignmentStatus.LATE, AssignmentStatus.RESCHEDULED -> {
                                        Button(
                                            onClick = { onCheckInClick(item.store, item.assignment) },
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .height(48.dp)
                                                .testTag("checkin_btn_${item.markerNumber}")
                                        ) {
                                            Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(labels.checkIn)
                                        }
                                        OutlinedButton(
                                            onClick = { onRescheduleClick(item.store, item.assignment) },
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("reschedule_btn_${item.markerNumber}")
                                        ) {
                                            Icon(Icons.Filled.EventRepeat, contentDescription = labels.reschedule, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    AssignmentStatus.CHECKED_IN -> {
                                        Button(
                                            onClick = { onCheckOutClick(item.store, item.assignment) },
                                            modifier = Modifier
                                                .weight(1.4f)
                                                .height(48.dp)
                                                .testTag("checkout_btn_${item.markerNumber}")
                                        ) {
                                            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
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
    }
}

@Composable
private fun GpsBannerCard(
    locationState: LocationState,
    radiusMeters: Int,
    onRequestLocationPermission: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (locationState is LocationState.Unavailable || locationState is LocationState.PermissionRequired) {
                StatusErrorBg
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = "GPS Status",
                    tint = when (locationState) {
                        is LocationState.Available -> StatusSuccess
                        is LocationState.Searching -> StatusInfo
                        else -> StatusError
                    }
                )
                Column {
                    Text(
                        text = when (locationState) {
                            is LocationState.Available -> "Live GPS Locked (±${locationState.accuracyMeters.toInt()} m)"
                            is LocationState.Searching -> "Acquiring GPS Signal…"
                            is LocationState.PermissionRequired -> "GPS Permission Required"
                            is LocationState.Unavailable -> "GPS Unavailable"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = when (locationState) {
                            is LocationState.Available -> "${String.format("%.5f", locationState.latitude)}, ${String.format("%.5f", locationState.longitude)} • Check-in radius ${radiusMeters}m"
                            is LocationState.Searching -> "Updating store distances automatically"
                            is LocationState.PermissionRequired -> "Grant location access to calculate store distances & check in"
                            is LocationState.Unavailable -> locationState.reason
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (locationState is LocationState.PermissionRequired) {
                Button(onClick = onRequestLocationPermission) {
                    Text("Allow")
                }
            }
        }
    }
}

@Composable
private fun InteractiveStoreCanvasMap(
    stores: List<StoreWithDistance>,
    locationState: LocationState,
    selectedAssignmentId: String?,
    radiusMeters: Int,
    onSelectStore: (StoreWithDistance) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val textMeasurer: TextMeasurer = rememberTextMeasurer()

    val visitorLoc = locationState as? LocationState.Available
    val allLats = buildList {
        stores.forEach { add(it.store.latitude) }
        visitorLoc?.let { add(it.latitude) }
    }
    val allLngs = buildList {
        stores.forEach { add(it.store.longitude) }
        visitorLoc?.let { add(it.longitude) }
    }

    val centerLat = allLats.average()
    val centerLng = allLngs.average()
    val latSpan = max((allLats.maxOrNull() ?: 0.0) - (allLats.minOrNull() ?: 0.0), 0.01)
    val lngSpan = max((allLngs.maxOrNull() ?: 0.0) - (allLngs.minOrNull() ?: 0.0), 0.01)

    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .background(Color(0xFF0E1D24))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.75f, 4f)
                            panOffset += pan
                        }
                    }
            ) {
                val pad = 48.dp.toPx()
                val usableW = (size.width - pad * 2).coerceAtLeast(1f)
                val usableH = (size.height - pad * 2).coerceAtLeast(1f)

                // Draw subtle spatial grid
                val gridColor = Color(0xFF1F3842)
                for (i in 1..6) {
                    val x = size.width * (i / 7f)
                    val y = size.height * (i / 7f)
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }

                fun toCanvasPoint(lat: Double, lng: Double): Offset {
                    val normX = ((lng - centerLng) / lngSpan).toFloat()
                    val normY = ((centerLat - lat) / latSpan).toFloat()
                    val cx = size.width / 2f + normX * usableW * 0.72f * scale + panOffset.x
                    val cy = size.height / 2f + normY * usableH * 0.72f * scale + panOffset.y
                    return Offset(cx, cy)
                }

                val visitorPoint = visitorLoc?.let { toCanvasPoint(it.latitude, it.longitude) }
                val nearestStore = stores.firstOrNull { it.isNearestPending }
                if (visitorPoint != null && nearestStore != null) {
                    val targetPoint = toCanvasPoint(nearestStore.store.latitude, nearestStore.store.longitude)
                    drawLine(
                        color = Color(0xFFF59E0B),
                        start = visitorPoint,
                        end = targetPoint,
                        strokeWidth = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                }

                // Draw stores
                stores.forEach { item ->
                    val pt = toCanvasPoint(item.store.latitude, item.store.longitude)
                    val isSelected = item.assignment.id == selectedAssignmentId
                    val isDone = item.assignment.status == AssignmentStatus.COMPLETED

                    if (isSelected) {
                        // Approximate visual geofence ring
                        val metersPerDegLat = 111_320.0 * cos(Math.toRadians(centerLat)).coerceAtLeast(0.2)
                        val radiusPx = ((radiusMeters / metersPerDegLat) / latSpan).toFloat() * usableH * 0.72f * scale
                        val displayRing = radiusPx.coerceIn(22.dp.toPx(), 64.dp.toPx())
                        drawCircle(
                            color = Color(0xFF14B8A6).copy(alpha = 0.18f),
                            radius = displayRing,
                            center = pt
                        )
                        drawCircle(
                            color = Color(0xFF14B8A6),
                            radius = displayRing,
                            center = pt,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    val pinColor = when {
                        item.isNearestPending -> Color(0xFFF59E0B)
                        isDone -> Color(0xFF10B981)
                        else -> Color(0xFF0EA5E9)
                    }

                    drawCircle(
                        color = Color.White,
                        radius = if (isSelected) 17.dp.toPx() else 14.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = pinColor,
                        radius = if (isSelected) 14.dp.toPx() else 11.dp.toPx(),
                        center = pt
                    )

                    val numLayout = textMeasurer.measure(
                        text = item.markerNumber.toString(),
                        style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                    drawText(
                        textLayoutResult = numLayout,
                        topLeft = Offset(pt.x - numLayout.size.width / 2f, pt.y - numLayout.size.height / 2f)
                    )
                }

                // Draw live visitor GPS pulse
                visitorPoint?.let { vp ->
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.25f),
                        radius = 22.dp.toPx(),
                        center = vp
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 9.dp.toPx(),
                        center = vp
                    )
                    drawCircle(
                        color = Color(0xFF0284C7),
                        radius = 6.dp.toPx(),
                        center = vp
                    )
                }
            }

            // Legend + Reset zoom overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(8.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.NearMe, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                        Text("Pinch/Drag Map", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clickable {
                        scale = 1f
                        panOffset = Offset.Zero
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.GpsFixed, contentDescription = "Reset Map View", tint = Color.White, modifier = Modifier.size(14.dp))
                    Text("Reset", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
