/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.kotlin.dsl.dcl

import org.gradle.kotlin.dsl.accessors.DCL_ENABLED_PROPERTY_NAME
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.junit.Test
import kotlin.test.assertTrue

class DclInterpreterIntegrationTest : AbstractKotlinIntegrationTest() {
    @Test
    fun `declarative files get interpreted with the DCL interpreter`() {
        withCustomSoftwarePluginWithContainer()
        enableDclInGradleProperties()

        withBuildScript(
            """
            mySoftwareType {
                myElements {
                    myElement("foo") { }
                    myElement("bar") { }
                }
            }
            """.trimIndent()
        )

        with(build("printNames", "--info")) {
            assertOutputContains("Successfully interpreted Kotlin DSL script ${file("build.gradle.kts").absolutePath} with DCL")
            assertOutputContains("[bar, foo]")
        }

        file("build.gradle.kts").appendText("\nprintln(\"not declarative anymore!\")")

        with(build("printNames", "--info")) {
            assertOutputContains("not declarative anymore!")
            assertOutputContains("[bar, foo]")
            assertTrue {
                output.lines().single { "Failed to interpret Kotlin DSL script ${file("build.gradle.kts").absolutePath} with DCL." in it }
                    .contains("UnresolvedFunctionCallSignature(functionCall=FunctionCall(receiver=null, name=println")
            }
        }
    }

    private fun withCustomSoftwarePluginWithContainer() {
        withEcosystemAndPluginBuildInBuildLogic()

        withEcosystemPluginRegisteringMyPlugin()

        withFile(
            "build-logic/src/main/kotlin/MyPlugin.kt", """
                package com.example

                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import org.gradle.api.Named
                import org.gradle.api.NamedDomainObjectContainer
                import org.gradle.api.internal.plugins.software.SoftwareType
                import javax.inject.Inject

                abstract class MyPlugin @Inject constructor(private val project: Project) : Plugin<Project> {
                    @get:SoftwareType(name = "mySoftwareType")
                    abstract val mySoftwareType: MyExtension

                    override fun apply(project: Project) {
                        project.tasks.register("printNames") {
                            val names = mySoftwareType.myElements.names
                            doFirst {
                                println(names)
                            }
                        }
                    }
                }

                abstract class MyExtension {
                    abstract val myElements: NamedDomainObjectContainer<MyElement>
                }

                abstract class MyElement(val elementName: String) : Named {
                    override fun getName() = elementName
                }
                """.trimIndent()
        )
    }

    private fun withEcosystemAndPluginBuildInBuildLogic() {
        withFile(
            "build-logic/build.gradle.kts", """
                plugins {
                    id("java-gradle-plugin")
                    `kotlin-dsl`
                }

                repositories {
                    mavenCentral()
                }

                gradlePlugin {
                    plugins {
                        create("myPlugin") {
                            id = "com.example.myPlugin"
                            implementationClass = "com.example.MyPlugin"
                        }
                        create("myEcosystemPlugin") {
                            id = "com.example.myEcosystemPlugin"
                            implementationClass = "com.example.MyEcosystemPlugin"
                        }
                    }
                }
            """.trimIndent()
        )

        withFile(
            "settings.gradle.kts", """
            pluginManagement {
                includeBuild("build-logic")
            }

            plugins {
                id("com.example.myEcosystemPlugin")
            }
        """.trimIndent()
        )
    }

    private fun withEcosystemPluginRegisteringMyPlugin() {
        withFile(
            "build-logic/src/main/kotlin/MyEcosystemPlugin.kt", """
                package com.example

                import org.gradle.api.Plugin
                import org.gradle.api.initialization.Settings
                import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes

                @RegistersSoftwareTypes(MyPlugin::class)
                class MyEcosystemPlugin : Plugin<Settings> {
                    override fun apply(settings: Settings) = Unit
                }
            """.trimIndent()
        )
    }

    private fun enableDclInGradleProperties() =
        withFile("gradle.properties").appendText("\n$DCL_ENABLED_PROPERTY_NAME=true\n")
}
