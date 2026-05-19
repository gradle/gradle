package org.example

import org.gradle.testkit.runner.GradleRunner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.io.File

class BuildLogicFunctionalTest {

    @Rule
    @JvmField
    val testProjectDir: TemporaryFolder = TemporaryFolder()

    lateinit var buildFile: File

    @Before
    fun setup() {
        testProjectDir.newFile("settings.gradle").writeText("")
        buildFile = testProjectDir.newFile("build.gradle")
    }

    // tag::functional-test-configuration-cache[]
    @Test
    fun `my task can be loaded from the configuration cache`() {

        buildFile.writeText("""
            plugins {
                id 'org.example.my-plugin'
            }
        """)

        runner()
            .withArguments("--configuration-cache", "myTask")        // <1>
            .build()

        val result = runner()
            .withArguments("--configuration-cache", "myTask")        // <2>
            .build()

        require(result.output.contains("Reusing configuration cache.")) // <3>
        // ... more assertions on your task behavior
    }
    // end::functional-test-configuration-cache[]

    private
    fun runner() =
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
}
