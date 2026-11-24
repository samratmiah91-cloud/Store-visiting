package com.example

import com.example.data.local.ApprovalStatus
import com.example.data.local.AssignmentStatus
import com.example.data.local.AttendanceStatus
import com.example.data.local.NotificationType
import com.example.data.local.PermissionKey
import com.example.data.local.RequestType
import com.example.data.local.RolePermissionEntity
import com.example.data.local.RoomConverters
import com.example.data.local.SyncStatus
import com.example.data.local.TaskStatus
import com.example.data.local.UserRole
import com.example.ui.components.formatInr
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.labelsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreVisitUnitTest {

    @Test
    fun `role hierarchy ranks and default permissions are enforced`() {
        assertTrue(UserRole.MAIN_SUPER_ADMIN.hierarchyRank > UserRole.ADMIN.hierarchyRank)
        assertTrue(UserRole.ADMIN.hierarchyRank > UserRole.MANAGER.hierarchyRank)
        assertTrue(UserRole.MANAGER.hierarchyRank > UserRole.SUPERVISOR.hierarchyRank)
        assertTrue(UserRole.SUPERVISOR.hierarchyRank > UserRole.VISITOR.hierarchyRank)

        val msaPerms = RolePermissionEntity.defaultsFor(UserRole.MAIN_SUPER_ADMIN)
        PermissionKey.entries.forEach { key ->
            assertTrue("Main Super Admin must have ${key.name}", msaPerms.hasPermission(key))
        }

        val adminPerms = RolePermissionEntity.defaultsFor(UserRole.ADMIN)
        assertFalse(adminPerms.canConfigureSettings)
        assertTrue(adminPerms.canManageUsers)

        val visitorPerms = RolePermissionEntity.defaultsFor(UserRole.VISITOR)
        assertTrue(visitorPerms.canCheckInOut)
        assertTrue(visitorPerms.canViewLiveMap)
        assertFalse(visitorPerms.canManageStores)
        assertFalse(visitorPerms.canMarkAttendance)

        val updatedVisitor = visitorPerms.withPermission(PermissionKey.VIEW_REPORTS, true)
        assertTrue(updatedVisitor.canViewReports)
    }

    @Test
    fun `room enum converters round-trip safely`() {
        val conv = RoomConverters()
        UserRole.entries.forEach { assertEquals(it, conv.toRole(conv.fromRole(it))) }
        AssignmentStatus.entries.forEach { assertEquals(it, conv.toAssignmentStatus(conv.fromAssignmentStatus(it))) }
        TaskStatus.entries.forEach { assertEquals(it, conv.toTaskStatus(conv.fromTaskStatus(it))) }
        AttendanceStatus.entries.forEach { assertEquals(it, conv.toAttendanceStatus(conv.fromAttendanceStatus(it))) }
        RequestType.entries.forEach { assertEquals(it, conv.toRequestType(conv.fromRequestType(it))) }
        ApprovalStatus.entries.forEach { assertEquals(it, conv.toApprovalStatus(conv.fromApprovalStatus(it))) }
        NotificationType.entries.forEach { assertEquals(it, conv.toNotificationType(conv.fromNotificationType(it))) }
        SyncStatus.entries.forEach { assertEquals(it, conv.toSyncStatus(conv.fromSyncStatus(it))) }

        // Unknown legacy enum strings fall back gracefully without throwing
        assertEquals(UserRole.VISITOR, conv.toRole("UNKNOWN_ROLE"))
        assertEquals(AssignmentStatus.PENDING, conv.toAssignmentStatus("UNKNOWN_STATUS"))
    }

    @Test
    fun `bilingual labels and INR formatting work properly`() {
        val en = labelsFor(AppLanguage.ENGLISH)
        val hi = labelsFor(AppLanguage.HINDI_BILINGUAL)
        assertEquals("Check In", en.checkIn)
        assertTrue(hi.checkIn.contains("चेक-इन"))
        assertTrue(formatInr(1250.0).contains("1,250"))
    }
}
