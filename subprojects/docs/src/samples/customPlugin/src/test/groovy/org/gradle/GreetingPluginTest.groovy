package org.gradle

import org.junit.Test
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import static org.junit.Assert.*

// START SNIPPET test-plugin
class GreetingPluginTest {
    @Test
    public void greeterPluginAddsGreetingTaskToProject() {
        Project project = ProjectBuilder.builder().build()
        project.apply plugin: 'greeting'

        assertTrue(project.tasks.hello instanceof GreetingTask)
    }
}
// END SNIPPET test-plugin
