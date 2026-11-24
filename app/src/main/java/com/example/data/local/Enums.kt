package com.example.data.local

enum class UserRole(val displayName: String, val hierarchyRank: Int) {
    MAIN_SUPER_ADMIN("Main Super Admin", 5),
    ADMIN("Admin", 4),
    MANAGER("Manager", 3),
    SUPERVISOR("Supervisor", 2),
    VISITOR("Visitor", 1)
}

enum class AssignmentStatus(val label: String) {
    PENDING("Pending"),
    CHECKED_IN("Checked In"),
    COMPLETED("Completed"),
    LATE("Late"),
    MISSED("Missed"),
    CANCELLED("Cancelled"),
    RESCHEDULED("Rescheduled")
}

enum class TaskStatus(val label: String) {
    PENDING("Pending"),
    COMPLETED("Completed")
}

enum class AttendanceStatus(val label: String) {
    PRESENT("Present"),
    ABSENT("Absent"),
    LATE("Late")
}

enum class RequestType(val label: String) {
    CANCEL("Cancel Visit"),
    RESCHEDULE("Reschedule Visit")
}

enum class ApprovalStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected")
}

enum class NotificationType(val label: String) {
    ASSIGNMENT("Store Assignment"),
    TASK("Task Alert"),
    RESCHEDULE("Reschedule Update"),
    REMINDER("Visit Reminder"),
    LATE_MISSED("Late / Missed Alert"),
    ANNOUNCEMENT("Admin Announcement")
}

enum class SyncStatus(val label: String) {
    SYNCED("Synced"),
    PENDING_SYNC("Pending Sync")
}

enum class PermissionKey(val label: String, val description: String) {
    MANAGE_USERS("Manage Users", "Create, approve, and assign roles to users"),
    MANAGE_STORES("Manage Stores", "Add, edit, and configure store GPS locations"),
    ASSIGN_STORES("Assign Stores", "Assign stores to visitors without daily cap"),
    CREATE_TASKS("Create Tasks", "Create and assign store tasks to visitors"),
    MARK_ATTENDANCE("Mark Attendance", "Mark daily Present/Absent/Late attendance"),
    APPROVE_RESCHEDULE("Approve Reschedules", "Approve or reject cancel/reschedule requests"),
    VIEW_REPORTS("View Reports", "Access visitor, store, task, and attendance reports"),
    EXPORT_REPORTS("Export Reports", "Export reports to Excel (CSV) and PDF"),
    SEND_ANNOUNCEMENTS("Send Announcements", "Broadcast system announcements"),
    CONFIGURE_SETTINGS("App Settings & Backup", "Configure late threshold, radius, and backups"),
    CHECK_IN_OUT("Check-In & Check-Out", "Perform GPS + photo check-in and gain check-out"),
    VIEW_LIVE_MAP("Live Store Map", "View interactive store map and Google Maps navigation")
}
