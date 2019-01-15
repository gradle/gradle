package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.normalisedPath

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

        withFile("src/main/kotlin/plugin-without-package.gradle.kts", "\n")
        withFile("src/main/kotlin/plugins/plugin-with-package.gradle.kts", """
            package plugins
        """)

        executer.expectDeprecationWarning()
        build("generateScriptPluginAdapters")

        executer.expectDeprecationWarnings(2)
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

        executer.expectDeprecationWarning()
        build(firstDir, "classes", "--build-cache").apply {
            assertTaskExecuted(generationTask)
        }

        executer.expectDeprecationWarning()
        build(firstDir, "classes", "--build-cache").apply {
            assertOutputContains("$generationTask UP-TO-DATE")
        }

        executer.expectDeprecationWarning()
        build(secondDir, "classes", "--build-cache").apply {
            assertOutputContains("$generationTask FROM-CACHE")
        }
    }
}
