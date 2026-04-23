package org.example

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

class SlackPluginTest {
    @Test fun `plugin registers task`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.example.slack")

        // Verify the result
        assertNotNull(project.tasks.findByName("sendTestSlackMessage"))
    }
}
