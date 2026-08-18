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
}
