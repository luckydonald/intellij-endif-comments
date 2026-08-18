package de.luckydonald.endifcomments.startup

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Temporary diagnostic: shows a balloon on project open so plugin initialization is directly
 * observable, independent of whether the Settings page / inspection / inlay each register
 * correctly. Remove once the "plugin has no effect" issue is confirmed fixed.
 */
class StartupNotifier : ProjectActivity {
    override suspend fun execute(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("de.luckydonald.endifcomments.startup")
            .createNotification(
                "Explicit Block Endings",
                "Explicit Block Endings loaded.",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }
}
