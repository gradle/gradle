package org.gradle

import org.junit.Test
import org.gradle.api.test.ProjectBuilder
import org.gradle.api.Project
import static org.junit.Assert.*

class GreeterPluginTest {
    @Test
    public void greeterPluginAddsGreetingTaskToProject() {
        Project project = ProjectBuilder.withProjectDir(new File('build/test')).create()
        project.apply plugin: 'greeting'

        assertTrue(project.tasks.hello instanceof Greeting)
    }
}