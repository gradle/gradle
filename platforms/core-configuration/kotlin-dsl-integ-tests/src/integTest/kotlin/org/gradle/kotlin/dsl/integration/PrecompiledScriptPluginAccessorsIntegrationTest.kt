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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginAccessorsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `accessors are available after script body change`() {

        withKotlinBuildSrc()
        val myPluginScript = withFile(
            "buildSrc/src/main/kotlin/my-plugin.gradle.kts",
            """
            plugins { base }

            base {
                archivesName.set("my")
            }

            println("base")
            """
        )

        withDefaultSettings()
        withBuildScript(
            """
            plugins {
                `my-plugin`
            }
            """
        )

        build("help").apply {
            assertThat(output, containsString("base"))
        }

        myPluginScript.appendText(
            """

            println("modified")
            """.trimIndent()
        )

        build("help").apply {
            assertThat(output, containsString("base"))
            assertThat(output, containsString("modified"))
        }
    }

    @Test
    fun `accessors are available after re-running tasks`() {

        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/my-plugin.gradle.kts",
            """
            plugins { base }

            base {
                archivesName.set("my")
            }
            """
        )

        withDefaultSettings()
        withBuildScript(
            """
            plugins {
                `my-plugin`
            }
            """
        )

        build("clean")

        build("clean", "--rerun-tasks")
    }

    @Test
    fun `accessors are available after registering plugin`() {
        withSettings(
            """
            $defaultSettingsScript

            include("consumer", "producer")
            """
        )

        withBuildScript(
            """
            plugins {
                `java-library`
            }

            allprojects {
                $repositoriesBlock
            }

            dependencies {
                api(project(":consumer"))
            }
            """
        )

        withFolders {

            "consumer" {
                withFile(
                    "build.gradle.kts",
                    """
                    plugins {
                        id("org.gradle.kotlin.kotlin-dsl")
                    }

                    // Forces dependencies to be visible as jars
                    // so we get plugin spec accessors
                    ${forceJarsOnCompileClasspath()}

                    dependencies {
                        implementation(project(":producer"))
                    }
                    """
                )

                withFile(
                    "src/main/kotlin/consumer-plugin.gradle.kts",
                    """
                    plugins { `producer-plugin` }
                    """
                )
            }

            "producer" {
                withFile(
                    "build.gradle",
                    """
                    plugins {
                        id("java-library")
                        id("java-gradle-plugin")
                    }
                    """
                )
                withFile(
                    "src/main/java/producer/ProducerPlugin.java",
                    """
                    package producer;
                    public class ProducerPlugin {
                        // Using internal class to verify https://github.com/gradle/gradle/issues/17619
                        public static class Implementation implements ${nameOf<Plugin<*>>()}<${nameOf<Project>()}> {
                            @Override public void apply(${nameOf<Project>()} target) {}
                        }
                    }
                    """
                )
            }
        }

        buildAndFail("assemble").run {
            // Accessor is not available on the first run as the plugin hasn't been registered.
            assertTaskExecuted(
                ":consumer:generateExternalPluginSpecBuilders"
            )
        }

        existing("producer/build.gradle").run {
            appendText(
                """
                gradlePlugin {
                    plugins {
                        producer {
                            id = 'producer-plugin'
                            implementationClass = 'producer.ProducerPlugin${'$'}Implementation'
                        }
                    }
                }
                """
            )
        }

        // Accessor becomes available after registering the plugin.
        build("assemble").run {
            assertTaskExecuted(
                ":consumer:generateExternalPluginSpecBuilders"
            )
        }
    }

    private
    inline fun <reified T> nameOf() = T::class.qualifiedName

    @Test
    fun `accessors are available after renaming precompiled script plugin from project dependency`() {

        withSettings(
            """
            $defaultSettingsScript

            include("consumer", "producer")
            """
        )

        withBuildScript(
            """
            plugins {
                `java-library`
                `kotlin-dsl` apply false
            }

            allprojects {
                $repositoriesBlock
            }

            dependencies {
                api(project(":consumer"))
            }
            """
        )

        withFolders {

            "consumer" {
                withFile(
                    "build.gradle.kts",
                    """
                    plugins {
                        id("org.gradle.kotlin.kotlin-dsl")
                    }

                    // Forces dependencies to be visible as jars
                    // to reproduce the failure that happens in forkingIntegTest.
                    // Incidentally, this also allows us to write `stable-producer-plugin`
                    // in the plugins block below instead of id("stable-producer-plugin").
                    ${forceJarsOnCompileClasspath()}

                    dependencies {
                        implementation(project(":producer"))
                    }
                    """
                )

                withFile(
                    "src/main/kotlin/consumer-plugin.gradle.kts",
                    """
                    plugins { `stable-producer-plugin` }
                    """
                )
            }

            "producer" {
                withFile(
                    "build.gradle.kts",
                    """
                    plugins { id("org.gradle.kotlin.kotlin-dsl") }
                    """
                )
                withFile("src/main/kotlin/changing-producer-plugin.gradle.kts")
                withFile(
                    "src/main/kotlin/stable-producer-plugin.gradle.kts",
                    """
                    println("*42*")
                    """
                )
            }
        }

        build("assemble").run {
            assertTaskExecuted(
                ":consumer:generateExternalPluginSpecBuilders"
            )
        }

        existing("producer/src/main/kotlin/changing-producer-plugin.gradle.kts").run {
            renameTo(resolveSibling("changed-producer-plugin.gradle.kts"))
        }

        build("assemble").run {
            assertTaskExecuted(
                ":consumer:generateExternalPluginSpecBuilders"
            )
        }
    }

    private
    fun forceJarsOnCompileClasspath() = """
        configurations {
            compileClasspath {
                attributes {
                    attribute(
                        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        objects.named(LibraryElements.JAR)
                    )
                }
            }
        }
    """
}
