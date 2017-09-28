package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.testkit.runner.BuildResult

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertThat
import org.junit.Assume.assumeThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import java.io.File


@RunWith(Parameterized::class)
class SamplesSmokeTest(val sampleDir: File) : AbstractIntegrationTest() {

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun testCases(): Iterable<File> =
            samplesRootDir.listFiles().filter { it.isDirectory }
    }

    @Before
    fun populateProjectRootWithSample() {
        assumeThat(sampleDir.name, not(containsString("android")))
        copySampleProject(from = sampleDir, to = projectRoot)
    }

    @Test
    fun `tasks task succeeds on `() {
        build("tasks")
    }

    @Test
    fun `uses the right Kotlin Gradle Plugin version`() {

        var kgpFound = false

        fun assertKotlinGradlePluginVersion(result: BuildResult) {
            if (result.output.contains(":kotlin-gradle-plugin:")) {
                kgpFound = true
                assertThat(result.output, containsString(":kotlin-gradle-plugin:$embeddedKotlinVersion"))
            }
        }

        if (File(sampleDir, "buildSrc").isDirectory) {
            // TODO:pm uncomment once `kotlin-dsl` plugin is published
            // assertKotlinGradlePluginVersion(build("-p", "buildSrc", "buildEnvironment", "-q"))
        }
        assertKotlinGradlePluginVersion(build("buildEnvironment", "-q"))
        listSubProjectPaths().forEach { projectPath ->
            assertKotlinGradlePluginVersion(build("$projectPath:buildEnvironment", "-q"))
        }

        // Mark that test as ignored if not using the KGP
        assumeTrue(kgpFound)
    }

    private
    val extractSubProjectPaths = Regex("""Project '(:.*)'""")

    private
    fun listSubProjectPaths() =
        build("projects", "-q").output.lines()
            .filter { extractSubProjectPaths.containsMatchIn(it) }
            .map { extractSubProjectPaths.find(it)!!.groupValues[1] }
}
