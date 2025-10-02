package org.example

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PublishingGuardPluginTest {

    @TempDir
    lateinit var testProjectDir: File

    private fun write(file: File, text: String) {
        file.parentFile.mkdirs()
        file.writeText(text.trimIndent())
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments(*args)
            .withPluginClasspath()
            .forwardOutput()

    private fun createBuild(version: String) {
        write(File(testProjectDir, "settings.gradle.kts"), """
            rootProject.name = "test-project"
        """)
        write(File(testProjectDir, "build.gradle.kts"), """
            plugins { 
              id("org.example.pubcheck") 
            }
            version = "$version"
        """)
    }

    private fun problemsReport(): File =
        File(testProjectDir, "build/reports/problems/problems-report.html")

    @Test
    fun `version 1 fails the build (throws)`() {
        createBuild(version = "1")

        val result = runner("pubcheck").buildAndFail()
        val out = result.output

        assertTrue(out.contains("Version '1' not allowed"),
            "Expected the error label in output.\n$out")
        assertTrue(problemsReport().exists(), "Expected problems HTML report to be generated.")
    }

    @Test
    fun `version 2 reports a warning but build succeeds`() {
        createBuild(version = "2")

        val result: BuildResult = runner("pubcheck").build()
        val out = result.output

        assertEquals(TaskOutcome.SUCCESS, result.task(":pubcheck")?.outcome)
        assertTrue(problemsReport().exists(), "Expected problems HTML report to be generated for warning.")
    }

    @Test
    fun `other versions pass silently`() {
        createBuild(version = "3")

        val result: BuildResult = runner("pubcheck").build()
        val out = result.output

        assertEquals(TaskOutcome.SUCCESS, result.task(":pubcheck")?.outcome)
        assertFalse(problemsReport().exists(), "Did not expect a problems report for clean build.")
    }
}
