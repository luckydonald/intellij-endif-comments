package de.luckydonald.endifcomments

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * Drives a real running IDE (via the `robot-server` plugin started by the `runIdeForUiTests`
 * Gradle task) to catch the class of bug where `plugin.xml` parses fine and the plugin shows
 * "enabled" in Settings > Plugins, but none of its `<extensions>` actually registered — e.g. a
 * misspelled `defaultExtensionNs`/`defaultExtensionPoint` attribute, which silently drops every
 * extension in the block instead of failing the build.
 */
class EndCommentUiTest {

    private val remoteRobot = RemoteRobot(System.getProperty("remote-robot-url") ?: "http://127.0.0.1:8082")

    @Test
    fun `all plugin xml extensions are actually registered`() {
        waitFor(Duration.ofSeconds(60), Duration.ofSeconds(1)) {
            remoteRobot.findAll<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']")).isNotEmpty()
        }

        val report = remoteRobot.callJs<String>(
            """
            importClass(com.intellij.ide.plugins.PluginManagerCore);
            importClass(com.intellij.openapi.extensions.PluginId);
            importClass(com.intellij.notification.NotificationGroupManager);
            importClass(com.intellij.openapi.extensions.ExtensionPointName);

            var descriptor = PluginManagerCore.getPlugin(PluginId.getId("de.luckydonald.endifcomments"));
            var sb = "";
            sb += "pluginFound=" + (descriptor != null) + ";";
            sb += "isEnabled=" + (descriptor != null && descriptor.isEnabled()) + ";";

            var group = NotificationGroupManager.getInstance().getNotificationGroup("de.luckydonald.endifcomments.startup");
            sb += "notificationGroupFound=" + (group != null) + ";";

            function containsMarker(epName, marker) {
                var list = ExtensionPointName.create(epName).getExtensionList();
                for (var i = 0; i < list.size(); i++) {
                    if (String(list.get(i)).indexOf(marker) >= 0) { return true; }
                }
                return false;
            }

            sb += "postStartupActivityFound=" + containsMarker("com.intellij.postStartupActivity", "StartupNotifier") + ";";
            sb += "inspectionFound=" + containsMarker("com.intellij.localInspection", "RedundantEndComment") + ";";
            sb += "configurableFound=" + (containsMarker("com.intellij.applicationConfigurable", "EndCommentConfigurable") || containsMarker("com.intellij.applicationConfigurable", "Explicit Block Endings")) + ";";
            sb += "highlightingPassFound=" + containsMarker("com.intellij.highlightingPassFactory", "EndCommentPassFactory") + ";";

            sb;
            """,
        )

        val fields = report.trimEnd(';').split(";").associate {
            val (key, value) = it.split("=", limit = 2)
            key to value
        }

        for ((key, value) in fields) {
            assertTrue("$key was $value (expected true) -- full report: $report", value == "true")
        }
    }

    @Test
    fun `a real hand-typed end comment gets flagged by the daemon`() {
        waitFor(Duration.ofSeconds(60), Duration.ofSeconds(1)) {
            remoteRobot.findAll<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']")).isNotEmpty()
        }

        val openReport = remoteRobot.callJs<String>(
            """
            importClass(com.intellij.openapi.project.ProjectManager);
            importClass(com.intellij.openapi.vfs.LocalFileSystem);
            importClass(com.intellij.openapi.fileEditor.FileEditorManager);
            importClass(com.intellij.openapi.fileEditor.OpenFileDescriptor);

            var project = ProjectManager.getInstance().getOpenProjects()[0];
            var path = project.getBasePath() + "/manual_end_comment.py";
            var vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
            var sb = "vFileFound=" + (vFile != null) + ";";
            if (vFile != null) {
                var descriptor = new OpenFileDescriptor(project, vFile);
                FileEditorManager.getInstance(project).openEditor(descriptor, true);
                sb += "opened=true;";
            }
            sb;
            """,
            true,
        )
        println("OPEN REPORT: $openReport")

        val report = remoteRobot.callJs<String>(
            """
            importClass(com.intellij.openapi.project.ProjectManager);
            importClass(com.intellij.openapi.vfs.LocalFileSystem);
            importClass(com.intellij.openapi.fileEditor.FileDocumentManager);
            importClass(com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl);
            importClass(com.intellij.lang.annotation.HighlightSeverity);

            var project = ProjectManager.getInstance().getOpenProjects()[0];
            var path = project.getBasePath() + "/manual_end_comment.py";
            var vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
            var document = FileDocumentManager.getInstance().getDocument(vFile);
            var sb = "documentFound=" + (document != null) + ";";

            var found = false;
            var descriptions = "";
            var attempts = 0;
            while (attempts < 30 && !found) {
                var highlights = DaemonCodeAnalyzerImpl.getHighlights(document, HighlightSeverity.WARNING, project);
                descriptions = "";
                for (var i = 0; i < highlights.size(); i++) {
                    var desc = highlights.get(i).getDescription();
                    descriptions += "[" + desc + "]";
                    if (desc != null && String(desc).indexOf("Redundant") >= 0) { found = true; }
                }
                if (!found) { java.lang.Thread.sleep(1000); attempts++; }
            }
            sb += "attempts=" + attempts + ";";
            sb += "redundantWarningFound=" + found + ";";
            sb += "lastDescriptions=" + descriptions + ";";
            sb;
            """,
        )

        val fields = report.trimEnd(';').split(";").associate {
            val (key, value) = it.split("=", limit = 2)
            key to value
        }
        assertTrue("documentFound was false -- full report: $report", fields["documentFound"] == "true")
        assertTrue(
            "the real '# end def' comment was never flagged -- full report: $report",
            fields["redundantWarningFound"] == "true",
        )
    }
}
