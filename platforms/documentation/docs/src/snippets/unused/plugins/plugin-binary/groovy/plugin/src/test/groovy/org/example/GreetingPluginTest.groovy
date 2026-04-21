package org.example

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull

class GreetingPluginTest {

    @Test
    void 'plugin registers greet task'() {
        // Create an in-memory project and apply the plugin
        Project project = ProjectBuilder.builder().build()
        project.plugins.apply("org.example.greeting")

        // Verify the task is registered
        assertNotNull(project.tasks.findByName("greet"))
    }

    @Test
    void 'extension has sensible defaults'() {
        Project project = ProjectBuilder.builder().build()
        project.plugins.apply("org.example.greeting")

        def ext = project.extensions.getByType(GreetingExtension)

        // Defaults from the plugin:
        assertEquals("Hello from plugin", ext.message.get())
        // Default output file is build/greeting.txt
        def defaultFile = ext.outputFile.get().asFile
        assertEquals(project.layout.buildDirectory.file("greeting.txt").get().asFile, defaultFile)
    }
}
