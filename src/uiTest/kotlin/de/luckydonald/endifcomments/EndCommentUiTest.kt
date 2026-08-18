package de.luckydonald.endifcomments

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import org.junit.Test
import java.time.Duration

/**
 * Smoke test driving a real running IDE (via the `robot-server` plugin started by the `testIdeUi`
 * Gradle task) to catch the class of bug where `plugin.xml` parses fine and the plugin shows
 * "enabled" in Settings > Plugins, but nothing it registers actually took effect. Asserts on the
 * startup notification from `StartupNotifier`, which is the one signal that only appears if the
 * plugin's own code actually ran.
 */
class EndCommentUiTest {

    private val remoteRobot = RemoteRobot(System.getProperty("remote-robot-url") ?: "http://127.0.0.1:8082")

    @Test
    fun `plugin initializes and shows its startup notification`() {
        waitFor(Duration.ofSeconds(60), Duration.ofSeconds(1)) {
            remoteRobot.findAll<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']")).isNotEmpty()
        }

        waitFor(Duration.ofSeconds(30), Duration.ofSeconds(1)) {
            remoteRobot.findAll<ContainerFixture>(byXpath("//div[@class='NotificationBalloon']"))
                .any { it.hasText("Explicit Block Endings loaded") }
        }
    }
}
