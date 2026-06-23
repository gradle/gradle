package org.example

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GreetingPluginTest {

    @Test
    fun `plugin registers greet task`() {
        // Create an in-memory project and apply the plugin
        val project: Project = ProjectBuilder.builder().build()
        project.plugins.apply("org.example.greeting")

        // Verify the task is registered
        assertNotNull(project.tasks.findByName("greet"))
    }

    @Test
    fun `extension has sensible defaults`() {
        val project: Project = ProjectBuilder.builder().build()
        project.plugins.apply("org.example.greeting")

        val ext = project.extensions.getByType(GreetingExtension::class.java)

        // Defaults from the plugin:
        assertEquals("Hello from plugin", ext.message.get())
        // Default output file is build/greeting.txt
        val defaultFile = ext.outputFile.get().asFile
        assertEquals(project.layout.buildDirectory.file("greeting.txt").get().asFile, defaultFile)
    }
}
