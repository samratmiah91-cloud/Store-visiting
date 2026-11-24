package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AppNotificationEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatDateTime
import com.example.ui.components.statusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(
    notifications: List<AppNotificationEntity>,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notifications (${notifications.count { !it.isRead }} unread)", style = MaterialTheme.typography.headlineSmall)
                if (notifications.any { !it.isRead }) {
                    TextButton(onClick = onMarkAllRead) { Text("Mark All Read") }
                }
            }

            if (notifications.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Filled.NotificationsNone,
                    title = "All Caught Up",
                    message = "Store assignments, new tasks, reschedule updates, reminders, and admin announcements appear here.",
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        val (fg, bg) = statusColors(item.type.name)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMarkRead(item.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(text = item.type.label, color = fg, backgroundColor = bg)
                                    Text(formatDateTime(item.createdAt), style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold
                                )
                                Text(item.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
