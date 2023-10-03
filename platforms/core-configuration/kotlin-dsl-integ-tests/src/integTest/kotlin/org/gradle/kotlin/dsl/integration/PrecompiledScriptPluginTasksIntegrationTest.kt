/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.integration

import org.codehaus.groovy.runtime.StringGroovyMethods
import org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepository
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginTasksIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @Requires(
        IntegTestPreconditions.NotEmbeddedExecutor::class,
        reason = "ktlint plugin issue in embedded mode"
    )
    fun `generated code follows kotlin-dsl coding conventions`() {

        withBuildScript(
            """
            plugins {
                `kotlin-dsl`
                id("org.gradle.kotlin-dsl.ktlint-convention") version "0.8.0"
            }

            $repositoriesBlock
            """
        )

        withPrecompiledKotlinScript(
            "plugin-without-package.gradle.kts",
            """
            plugins {
                org.gradle.base
            }

            """.trimIndent()
        )
        withPrecompiledKotlinScript(
            "test/gradle/plugins/plugin-with-package.gradle.kts",
            """
            package test.gradle.plugins

            plugins {
                org.gradle.base
            }

            """.trimIndent()
        )

        // From ktlint
        executer.beforeExecute {
            it.expectDocumentedDeprecationWarning("The Project.getConvention() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions")
        }

        build("generateScriptPluginAdapters")

        build("ktlintCheck", "-x", "ktlintKotlinScriptCheck")
    }

    @Test
    fun `precompiled script plugins tasks are cached and relocatable`() {

        val firstLocation = "first-location"
        val secondLocation = "second-location"
        val cacheDir = newDir("cache-dir")

        withDefaultSettingsIn(firstLocation).appendText(
            """
            rootProject.name = "test"
            buildCache {
                local {
                    directory = file("${cacheDir.normalisedPath}")
                }
            }
            """
        )
        withBuildScriptIn(
            firstLocation,
            """
            plugins { `kotlin-dsl` }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
            """
        )

        withFile("$firstLocation/src/main/kotlin/plugin-without-package.gradle.kts")
        withFile(
            "$firstLocation/src/main/kotlin/plugins/plugin-with-package.gradle.kts",
            """
            package plugins
            """
        )


        val firstDir = existing(firstLocation)
        val secondDir = newDir(secondLocation)
        firstDir.copyRecursively(secondDir)

        val cachedTasks = listOf(
            ":extractPrecompiledScriptPluginPlugins",
            ":generateExternalPluginSpecBuilders",
            ":compilePluginsBlocks",
            ":generateScriptPluginAdapters"
        )
        val downstreamKotlinCompileTask = ":compileKotlin"

        // TODO: the Kotlin compile tasks check for cacheability using Task.getProject
        executer.beforeExecute {
            it.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")
        }

        build(firstDir, "classes", "--build-cache").apply {
            cachedTasks.forEach { assertTaskExecuted(it) }
            assertTaskExecuted(downstreamKotlinCompileTask)
        }

        build(firstDir, "classes", "--build-cache").apply {
            cachedTasks.forEach { assertOutputContains("$it UP-TO-DATE") }
            assertOutputContains("$downstreamKotlinCompileTask UP-TO-DATE")
        }

        build(secondDir, "classes", "--build-cache").apply {
            cachedTasks.forEach { assertOutputContains("$it FROM-CACHE") }
            assertOutputContains("$downstreamKotlinCompileTask FROM-CACHE")
        }
    }

    @Test
    fun `precompiled script plugins adapters generation clean stale outputs`() {

        withBuildScript(
            """
            plugins { `kotlin-dsl` }
            """
        )

        val fooScript = withFile("src/main/kotlin/foo.gradle.kts", "")

        build("generateScriptPluginAdapters")
        assertTrue(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/FooPlugin.kt").isFile)

        fooScript.renameTo(fooScript.parentFile.resolve("bar.gradle.kts"))

        build("generateScriptPluginAdapters")
        assertFalse(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/FooPlugin.kt").exists())
        assertTrue(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/BarPlugin.kt").isFile)
    }


    @Test
    fun `applied precompiled script plugin is reloaded upon change`() {
        // given:
        withFolders {
            "build-logic" {
                withFile(
                    "settings.gradle.kts",
                    """
                        $defaultSettingsScript
                        include("producer", "consumer")
                    """
                )
                withFile(
                    "build.gradle.kts",
                    """
                        plugins {
                            `kotlin-dsl` apply false
                        }

                        subprojects {
                            apply(plugin = "org.gradle.kotlin.kotlin-dsl")
                            $repositoriesBlock
                        }

                        project(":consumer") {
                            dependencies {
                                "implementation"(project(":producer"))
                            }
                        }
                    """
                )

                withFile(
                    "producer/src/main/kotlin/producer-plugin.gradle.kts",
                    """
                        println("*version 1*")
                    """
                )
                withFile(
                    "consumer/src/main/kotlin/consumer-plugin.gradle.kts",
                    """
                        plugins { id("producer-plugin") }
                    """
                )
            }
        }
        withSettings(
            """
                includeBuild("build-logic")
            """
        )
        withBuildScript(
            """
                plugins { id("consumer-plugin") }
            """
        )

        // when:
        build("help").run {
            // then:
            assertThat(
                output.count("*version 1*"),
                equalTo(1)
            )
        }

        // when:
        file("build-logic/producer/src/main/kotlin/producer-plugin.gradle.kts").text = """
            println("*version 2*")
        """
        build("help").run {
            // then:
            assertThat(
                output.count("*version 2*"),
                equalTo(1)
            )
        }
    }

    private
    fun CharSequence.count(text: CharSequence): Int =
        StringGroovyMethods.count(this, text)

    @Test
    fun `no warnings on absent directories in compilation classpath`() {
        withDefaultSettings().appendText("""include("producer", "consumer")""")
        withFile("producer/build.gradle.kts", """plugins { java }""")
        withKotlinDslPluginIn("consumer").appendText("""dependencies { implementation(project(":producer")) }""")
        withFile("consumer/src/main/kotlin/some.gradle.kts", "")
        build(":consumer:classes").apply {
            assertTaskExecuted(":consumer:compilePluginsBlocks")
            assertNotOutput("w: Classpath entry points to a non-existent location")
        }
        assertFalse(file("producer/build/classes/java/main").exists())
    }
}
