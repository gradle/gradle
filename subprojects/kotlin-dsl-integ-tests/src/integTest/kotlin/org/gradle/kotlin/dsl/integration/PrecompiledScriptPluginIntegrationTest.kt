package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.test.fixtures.file.LeaksFileHandles

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `generated code follows kotlin-dsl coding conventions`() {

        withDefaultSettings()
        withBuildScript("""
            plugins {
                `kotlin-dsl`
                id("org.gradle.kotlin-dsl.ktlint-convention") version "0.2.3"
            }

            repositories { jcenter() }
        """)

        withFile("src/main/kotlin/plugin-without-package.gradle.kts", "\n")
        withFile("src/main/kotlin/plugins/plugin-with-package.gradle.kts", """
            package plugins
        """)

        build("generateScriptPluginAdapters")

        executer.expectDeprecationWarning()
        build("ktlintC")
    }

    @Test
    fun `precompiled script plugins adapters generation is cached and relocatable`() {

        requireGradleDistributionOnEmbeddedExecuter()

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

        build(firstDir, "classes", "--build-cache").apply {
            assertTaskExecuted(generationTask)
        }

        build(firstDir, "classes", "--build-cache").apply {
            assertOutputContains("$generationTask UP-TO-DATE")
        }

        build(secondDir, "classes", "--build-cache").apply {
            assertOutputContains("$generationTask FROM-CACHE")
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
