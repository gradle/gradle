package org.example

import java.io.File
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GreetingPluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }
    private val buildFile by lazy { projectDir.resolve("build.gradle.kts") }

    @Test
    fun `can run greeting task`() {
        // Arrange: write a tiny build that applies the plugin
        settingsFile.writeText("") // single-project build
        buildFile.writeText(
            """
            plugins {
                id("org.example.greeting")
            }
            """.trimIndent()
        )

        // Act: execute the task in an isolated Gradle build
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()      // picks up your plugin-under-test from the test classpath
            .withArguments("greet")
            .forwardOutput()
            .build()

        // Assert: verify console output and successful task outcome
        assertTrue(result.task(":greet")?.outcome == TaskOutcome.SUCCESS)
    }
}
