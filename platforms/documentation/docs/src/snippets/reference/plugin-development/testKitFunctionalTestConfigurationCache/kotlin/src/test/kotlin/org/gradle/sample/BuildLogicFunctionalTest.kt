package org.gradle.sample

import org.gradle.testkit.runner.ConfigurationCacheOutcome
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertEquals
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

    // tag::functional-test-configuration-cache-outcome[]
    @Test
    fun `myTask's configuration cache entry is stored and then reused`() {
        buildFile.writeText("""
            plugins {
                id 'org.gradle.sample.my-plugin'
            }
        """)

        var result = runner()
            .withArguments("--configuration-cache", "myTask")       // <1>
            .build()

        assertEquals(ConfigurationCacheOutcome.STORED, result.configurationCacheOutcome)

        result = runner()
            .withArguments("--configuration-cache", "myTask")       // <2>
            .build()

        assertEquals(ConfigurationCacheOutcome.REUSED, result.configurationCacheOutcome)
    }
    // end::functional-test-configuration-cache-outcome[]

    private
    fun runner() =
        GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClasspath()
}
