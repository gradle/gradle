/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Test


class TestKitIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    fun `withPluginClasspath works`() {

        withDefaultSettings()

        withBuildScript(
            """

            plugins {
                `java-gradle-plugin`
                `kotlin-dsl`
            }

            gradlePlugin {
                plugins {
                    register("test") {
                        id = "test"
                        implementationClass = "plugin.TestPlugin"
                    }
                }
            }

            dependencies {
                implementation(kotlin("stdlib"))
                testImplementation("junit:junit:4.13")
                testImplementation("org.hamcrest:hamcrest-core:1.3")
            }

            $repositoriesBlock

            """
        )

        withFile(
            "src/main/kotlin/plugin/TestPlugin.kt",
            """

            package plugin

            import org.gradle.api.*
            import org.gradle.kotlin.dsl.*

            class TestPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.extensions.create("test", TestExtension::class)
                }
            }

            open class TestExtension {
                fun ack() = println("Ack!")
            }
            """
        )

        withFile(
            "src/test/kotlin/plugin/TestPluginTest.kt",
            """

            package plugin

            import org.gradle.testkit.runner.*
            import org.hamcrest.CoreMatchers.*
            import org.junit.*
            import org.hamcrest.MatcherAssert.assertThat
            import org.junit.rules.TemporaryFolder

            class TestPluginTest {

                @Test
                fun `test extension can be configured`() {

                    withBuildscript(""${'"'}
                        plugins {
                            id("test")
                        }

                        test {
                            ack()
                        }
                    ""${'"'})

                    assertThat(
                        build().output,
                        containsString("Ack!"))
                }

                private
                fun build(vararg arguments: String): BuildResult =
                    GradleRunner
                        .create()
                        .withProjectDir(temporaryFolder.root)
                        .withPluginClasspath()
                        .withArguments(*arguments)
                        .withTestKitDir(java.io.File("${executer.gradleUserHomeDir.normalisedPath}"))
                        .build()

                private
                fun withBuildscript(script: String) =
                    temporaryFolder.newFile("build.gradle.kts").apply {
                        writeText(script)
                    }

                @Rule @JvmField val temporaryFolder = TemporaryFolder()
            }
            """
        )

        println(
            build("test").output
        )
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    fun `generated accessors work in the debug mode`() {

        withDefaultSettings()

        withBuildScript(
            """

            plugins {
                `java-gradle-plugin`
                `kotlin-dsl`
            }

            gradlePlugin {
                plugins {
                    register("test") {
                        id = "test"
                        implementationClass = "plugin.TestPlugin"
                    }
                }
            }

            dependencies {
                implementation(kotlin("stdlib"))
                testImplementation("junit:junit:4.13")
                testImplementation("org.hamcrest:hamcrest-core:1.3")
            }

            $repositoriesBlock

            """
        )

        withFile(
            "src/main/kotlin/plugin/TestPlugin.kt",
            """

            package plugin

            import org.gradle.api.*
            import org.gradle.kotlin.dsl.*
            import org.gradle.api.provider.*

            class TestPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.extensions.create("myExtension", TestExtension::class)
                }
            }

            interface TestExtension {
                val say: Property<String>
            }
            """
        )

        withFile(
            "src/test/kotlin/plugin/TestPluginTest.kt",
            """

            package plugin

            import org.gradle.testkit.runner.*
            import org.hamcrest.CoreMatchers.*
            import org.junit.*
            import org.hamcrest.MatcherAssert.assertThat
            import org.junit.rules.TemporaryFolder
            import kotlin.io.path.appendText
            import kotlin.io.path.createFile

            class TestPluginTest {

                @Rule
                @JvmField
                val temporaryFolder = TemporaryFolder()

                @Test
                fun `test extension can be configured`() {
                    val projectDir = temporaryFolder.root
                    projectDir.toPath()
                        .resolve("build.gradle.kts")
                        .createFile()
                        .appendText(""${'"'}
                            import plugin.TestExtension

                            plugins {
                                id("test")
                            }

                            val myExtension = extensions.get("myExtension") as TestExtension
                            myExtension.say.set("Hi!")

                            myExtension {
                                say = "Oh!"
                            }
                        ""${'"'})

                    GradleRunner
                        .create()
                        .withProjectDir(projectDir)
                        .withPluginClasspath()
                        .withDebug(true)
                        .withTestKitDir(java.io.File("${executer.gradleUserHomeDir.normalisedPath}"))
                        .build()
                }
            }
            """
        )

        println(
            build("test").output
        )
    }
}
