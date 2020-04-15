package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.RepoScriptBlockUtil.jcenterRepository
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    @ToBeFixedForInstantExecution
    fun `generated code follows kotlin-dsl coding conventions`() {

        withBuildScript("""
            plugins {
                `kotlin-dsl`
                id("org.gradle.kotlin-dsl.ktlint-convention") version "0.4.1"
            }

            $repositoriesBlock
        """)

        withPrecompiledKotlinScript("plugin-without-package.gradle.kts", """
            plugins {
                org.gradle.base
            }
        """)
        withPrecompiledKotlinScript("org/gradle/plugins/plugin-with-package.gradle.kts", """
            package org.gradle.plugins

            plugins {
                org.gradle.base
            }
        """)

        build("generateScriptPluginAdapters")

        build("ktlintCheck", "-x", "ktlintKotlinScriptCheck")
    }

    @Test
    @ToBeFixedForInstantExecution
    fun `precompiled script plugins tasks are cached and relocatable`() {

        requireGradleDistributionOnEmbeddedExecuter()

        val firstLocation = "first-location"
        val secondLocation = "second-location"
        val cacheDir = newDir("cache-dir")

        withDefaultSettingsIn(firstLocation).appendText("""
            rootProject.name = "test"
            buildCache {
                local {
                    directory = file("${cacheDir.normalisedPath}")
                }
            }
        """)
        withBuildScriptIn(firstLocation, """
            plugins { `kotlin-dsl` }
            ${jcenterRepository(GradleDsl.KOTLIN)}
        """)

        withFile("$firstLocation/src/main/kotlin/plugin-without-package.gradle.kts")
        withFile("$firstLocation/src/main/kotlin/plugins/plugin-with-package.gradle.kts", """
            package plugins
        """)


        val firstDir = existing(firstLocation)
        val secondDir = newDir(secondLocation)
        firstDir.copyRecursively(secondDir)

        val cachedTasks = listOf(
            ":extractPrecompiledScriptPluginPlugins",
            ":generateExternalPluginSpecBuilders",
            ":compilePluginsBlocks",
            ":generatePrecompiledScriptPluginAccessors",
            ":generateScriptPluginAdapters"
        )
        val configurationTask = ":configurePrecompiledScriptDependenciesResolver"
        val downstreamKotlinCompileTask = ":compileKotlin"

        build(firstDir, "classes", "--build-cache").apply {
            cachedTasks.forEach { assertTaskExecuted(it) }
            assertTaskExecuted(configurationTask)
            assertTaskExecuted(downstreamKotlinCompileTask)
        }

        build(firstDir, "classes", "--build-cache").apply {
            cachedTasks.forEach { assertOutputContains("$it UP-TO-DATE") }
            assertTaskExecuted(configurationTask)
            assertOutputContains("$downstreamKotlinCompileTask UP-TO-DATE")
        }

        build(secondDir, "classes", "--build-cache").apply {
            cachedTasks.forEach { assertOutputContains("$it FROM-CACHE") }
            assertTaskExecuted(configurationTask)
            assertOutputContains("$downstreamKotlinCompileTask FROM-CACHE")
        }
    }

    @Test
    @ToBeFixedForInstantExecution
    fun `precompiled script plugins adapters generation clean stale outputs`() {

        withBuildScript("""
            plugins { `kotlin-dsl` }
            ${jcenterRepository(GradleDsl.KOTLIN)}
        """)

        val fooScript = withFile("src/main/kotlin/foo.gradle.kts", "")

        build("generateScriptPluginAdapters")
        assertTrue(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/FooPlugin.kt").isFile)

        fooScript.renameTo(fooScript.parentFile.resolve("bar.gradle.kts"))

        build("generateScriptPluginAdapters")
        assertFalse(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/FooPlugin.kt").exists())
        assertTrue(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/BarPlugin.kt").isFile)
    }

    @Test
    @ToBeFixedForInstantExecution(because = "Kotlin Gradle Plugin")
    fun `can apply precompiled script plugin from groovy script`() {

        requireGradleDistributionOnEmbeddedExecuter()

        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", """
            tasks.register("myTask") {}
        """)

        withDefaultSettings()
        withFile("build.gradle", """
            plugins {
                id 'my-plugin'
            }
        """)

        build("myTask")
    }

    @Test
    @ToBeFixedForInstantExecution
    fun `accessors are available after script body change`() {

        requireGradleDistributionOnEmbeddedExecuter()

        withKotlinBuildSrc()
        val myPluginScript = withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", """
            plugins { base }

            base.archivesBaseName = "my"

            println("base")
        """)

        withDefaultSettings()
        withBuildScript("""
            plugins {
                `my-plugin`
            }
        """)

        build("help").apply {
            assertThat(output, containsString("base"))
        }

        myPluginScript.appendText("""

            println("modified")
        """.trimIndent())

        build("help").apply {
            assertThat(output, containsString("base"))
            assertThat(output, containsString("modified"))
        }
    }

    @Test
    @ToBeFixedForInstantExecution(because = "Kotlin Gradle Plugin")
    fun `accessors are available after re-running tasks`() {

        requireGradleDistributionOnEmbeddedExecuter()

        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/my-plugin.gradle.kts", """
            plugins { base }

            base.archivesBaseName = "my"
        """)

        withDefaultSettings()
        withBuildScript("""
            plugins {
                `my-plugin`
            }
        """)

        build("clean")

        build("clean", "--rerun-tasks")
    }
}
