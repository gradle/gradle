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

import org.junit.Test


class TestKitIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    fun `withPluginClasspath works`() {
        assumeNonEmbeddedGradleExecuter()

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
                implementation(kotlin("stdlib-jdk8"))
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
}
