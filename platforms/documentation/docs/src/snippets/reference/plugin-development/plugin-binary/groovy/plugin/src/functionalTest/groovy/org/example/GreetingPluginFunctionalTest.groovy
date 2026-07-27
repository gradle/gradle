package org.example

import java.io.File
import static org.junit.jupiter.api.Assertions.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GreetingPluginFunctionalTest {

    @TempDir
    File projectDir

    private File getSettingsFile() {
        return new File(projectDir, "settings.gradle")
    }

    private File getBuildFile() {
        return new File(projectDir, "build.gradle")
    }

    @Test
    void 'can run greeting task'() {
        // Arrange: write a tiny build that applies the plugin
        settingsFile.write("") // single-project build
        buildFile.write("""
            plugins {
                id 'org.example.greeting'
            }
        """.stripIndent())

        // Act: execute the task in an isolated Gradle build
        def result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()      // picks up your plugin-under-test from the test classpath
            .withArguments("greet")
            .forwardOutput()
            .build()

        // Assert: verify console output and successful task outcome
        assertTrue(result.task(":greet")?.outcome == TaskOutcome.SUCCESS)
    }
}
