package com.myorg

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

abstract class PluginTest {

    @Rule
    @JvmField
    val testProjectDir: TemporaryFolder = TemporaryFolder()
    protected lateinit var settingsFile: File
    protected lateinit var buildFile: File

    @Before
    fun setup() {
        settingsFile = testProjectDir.newFile("settings.gradle.kts")
        settingsFile.appendText("""
            rootProject.name = "test"
        """)
        buildFile = testProjectDir.newFile("build.gradle.kts")
    }

    fun runTask(task: String): BuildResult {
        return GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(task, "--stacktrace")
                .withPluginClasspath()
                .build()
    }

    fun runTaskWithFailure(task: String): BuildResult {
        return GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(task, "--stacktrace")
                .withPluginClasspath()
                .buildAndFail()
    }
}
