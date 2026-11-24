package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.UserEntity
import com.example.data.local.UserRole
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreVisitTopBar(
    user: UserEntity,
    isOnline: Boolean,
    pendingSyncCount: Int,
    unreadNotifications: Int,
    language: AppLanguage,
    labels: LocalizedLabels,
    onSyncClick: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenNotifications: () -> Unit,
    onLogout: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    RoleBadge(role = user.role)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { onSyncClick() }
                        .testTag("sync_status_pill")
                ) {
                    Icon(
                        imageVector = if (!isOnline) Icons.Filled.CloudOff else if (pendingSyncCount > 0) Icons.Filled.Sync else Icons.Filled.CloudDone,
                        contentDescription = if (isOnline) labels.online else labels.offline,
                        modifier = Modifier.size(14.dp),
                        tint = if (!isOnline) StatusWarning else if (pendingSyncCount > 0) StatusInfo else StatusSuccess
                    )
                    Text(
                        text = when {
                            !isOnline && pendingSyncCount > 0 -> "${labels.offline} • $pendingSyncCount ${labels.pendingSync}"
                            !isOnline -> labels.offline
                            pendingSyncCount > 0 -> "$pendingSyncCount ${labels.pendingSync} (${labels.syncNow})"
                            else -> labels.online
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!isOnline) StatusWarning else if (pendingSyncCount > 0) StatusInfo else StatusSuccess
                    )
                }
            }
        },
        actions = {
            IconButton(
                onClick = onToggleLanguage,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("language_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Translate,
                    contentDescription = "Switch Language (${language.label})",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onOpenNotifications,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("notifications_button")
            ) {
                BadgedBox(
                    badge = {
                        if (unreadNotifications > 0) {
                            Badge { Text(unreadNotifications.coerceAtMost(99).toString()) }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notifications"
                    )
                }
            }
            IconButton(
                onClick = onLogout,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("logout_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Logout"
                )
            }
        }
    )
}

@Composable
fun RoleBadge(role: UserRole) {
    val (bg, fg) = when (role) {
        UserRole.MAIN_SUPER_ADMIN -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        UserRole.ADMIN -> StatusInfoBg to StatusInfo
        UserRole.MANAGER -> StatusWarningBg to StatusWarning
        UserRole.SUPERVISOR -> StatusSuccessBg to StatusSuccess
        UserRole.VISITOR -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = role.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatInr(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return format.format(amount)
}

fun formatDateTime(millis: Long?): String {
    if (millis == null) return "-"
    return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(millis))
}

fun statusColors(status: String): Pair<Color, Color> = when (status.uppercase()) {
    "COMPLETED", "APPROVED", "PRESENT", "SYNCED", "SUBMITTED" -> StatusSuccess to StatusSuccessBg
    "CHECKED_IN", "RESCHEDULED", "ASSIGNMENT" -> StatusInfo to StatusInfoBg
    "PENDING", "LATE", "PENDING_SYNC" -> StatusWarning to StatusWarningBg
    "MISSED", "ABSENT", "CANCELLED", "REJECTED" -> StatusError to StatusErrorBg
    else -> StatusInfo to StatusInfoBg
}
