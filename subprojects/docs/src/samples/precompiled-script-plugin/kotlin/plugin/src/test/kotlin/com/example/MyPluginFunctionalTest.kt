package com.example

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.jvm.JvmField
import java.io.File

class BuildLogicFunctionalTest {

    @Rule
    @JvmField
    val testProjectDir: TemporaryFolder = TemporaryFolder()
    private lateinit var settingsFile: File
    private lateinit var buildFile: File

    @Before
    fun setup() {
        settingsFile = testProjectDir.newFile("settings.gradle.kts")
        buildFile = testProjectDir.newFile("build.gradle.kts")
    }

    @Test
    fun `greet task prints hello world`() {

        settingsFile.writeText("""
            rootProject.name = "test"
        """)
        buildFile.writeText("""
            plugins {
                id("com.example.my-plugin")
            }
        """)

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments("greet")
            .withPluginClasspath()
            .build()

        assertTrue(result.output.contains("Hello, World!"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":greet")?.outcome)
    }
}
