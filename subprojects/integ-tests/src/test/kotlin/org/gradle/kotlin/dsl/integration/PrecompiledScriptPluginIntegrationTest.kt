package org.gradle.kotlin.dsl.integration

import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

import org.gradle.kotlin.dsl.fixtures.gradleRunnerFor
import org.gradle.kotlin.dsl.fixtures.normalisedPath

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test


class PrecompiledScriptPluginIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `generated code follows kotlin-dsl coding conventions`() {

        withBuildScript("""
            plugins {
                `kotlin-dsl`
                id("org.gradle.kotlin-dsl.ktlint-convention") version "0.2.0"
            }

            repositories { jcenter() }
        """)

        withFile("src/main/kotlin/plugin-without-package.gradle.kts")
        withFile("src/main/kotlin/plugins/plugin-with-package.gradle.kts", """
            package plugins
        """)

        build("generateScriptPluginAdapters")
        build("ktlintC")
    }

    @Test
    fun `precompiled script plugins adapters generation is cached and relocatable`() {

        val firstLocation = "first-location"
        val secondLocation = "second-location"
        val cacheDir = newDir("cache-dir")

        withSettingsIn(firstLocation, """
            rootProject.name = "test"
            $pluginManagementBlock
            buildCache {
                local<DirectoryBuildCache> {
                    directory = file("${cacheDir.normalisedPath}")
                }
            }
        """)
        withBuildScriptIn(firstLocation, """
            plugins { `kotlin-dsl` }
            repositories { jcenter() }
        """)

        withFile("$firstLocation/src/main/kotlin/plugin-without-package.gradle.kts")
        withFile("$firstLocation/src/main/kotlin/plugins/plugin-with-package.gradle.kts", """
            package plugins
        """)


        val firstDir = existing(firstLocation)
        val secondDir = newDir(secondLocation)
        firstDir.copyRecursively(secondDir)

        val generationTask = ":generateScriptPluginAdapters"

        gradleRunnerFor(firstDir, "classes", "--build-cache").build().apply {
            assertThat(outcomeOf(generationTask), equalTo(SUCCESS))
        }
        gradleRunnerFor(firstDir, "classes", "--build-cache").build().apply {
            assertThat(outcomeOf(generationTask), equalTo(UP_TO_DATE))
        }
        gradleRunnerFor(secondDir, "classes", "--build-cache").build().apply {
            assertThat(outcomeOf(generationTask), equalTo(FROM_CACHE))
        }
    }

    @Test
    fun `precompiled script plugins adapters generation clean stale outputs`() {

        withDefaultSettings()
        withBuildScript("""
            plugins { `kotlin-dsl` }
            repositories { jcenter() }
        """)

        val fooScript = withFile("src/main/kotlin/foo.gradle.kts", "")

        build("generateScriptPluginAdapters")
        assertTrue(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/FooPlugin.kt").isFile)

        fooScript.renameTo(fooScript.parentFile.resolve("bar.gradle.kts"))

        build("generateScriptPluginAdapters")
        assertFalse(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/FooPlugin.kt").exists())
        assertTrue(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/BarPlugin.kt").isFile)
    }
}
