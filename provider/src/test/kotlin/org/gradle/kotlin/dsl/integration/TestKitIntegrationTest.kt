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

import org.gradle.api.JavaVersion

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.junit.Assume.assumeTrue

import org.junit.Test


class TestKitIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `withPluginClasspath works`() {

        assumeTrue(!JavaVersion.current().isJava9)

        withBuildScript("""

            plugins {
                `java-gradle-plugin`
                `kotlin-dsl`
            }

            gradlePlugin {
                (plugins) {
                    "test" {
                        id = "test"
                        implementationClass = "plugin.TestPlugin"
                    }
                }
            }

            dependencies {
                testImplementation("junit:junit:4.12")
            }

            repositories {
                jcenter()
            }
        """)

        withFile("src/main/kotlin/plugin/TestPlugin.kt", """

            package plugin

            import org.gradle.api.*

            class TestPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.extensions.create("test", TestExtension::class.java)
                }
            }

            open class TestExtension {
                fun ack() = println("Ack!")
            }
        """)

        withFile("src/test/kotlin/plugin/TestPluginTest.kt", """

            package plugin

            import org.gradle.testkit.runner.*
            import org.hamcrest.CoreMatchers.*
            import org.junit.*
            import org.junit.Assert.assertThat
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
                        .build()

                private
                fun withBuildscript(script: String) =
                    temporaryFolder.newFile("build.gradle.kts").apply {
                        writeText(script)
                    }

                @Rule @JvmField val temporaryFolder = TemporaryFolder()
            }
        """)

        println(
            build("test").output)
    }
}
