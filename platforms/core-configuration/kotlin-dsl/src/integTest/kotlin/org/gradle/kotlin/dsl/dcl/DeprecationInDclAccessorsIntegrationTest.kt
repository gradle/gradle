/*
 * Copyright 2025 the original author or authors.
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
import kotlin.test.Test

@Suppress("FunctionNaming")
class DeprecationInDclAccessorsIntegrationTest : AbstractKotlinIntegrationTest() {
    @Test
    fun `if DCL model types are deprecated, the Kotlin DSL accessors for them also get deprecated`() {
        withEcosystemAndPluginBuildInBuildLogic()
        withEcosystemPluginRegisteringMyPlugin()
        withDeclarativePlugin()
        withDclEnabledInGradleProperties()

        file("settings.gradle.kts").appendText("\n" + """
            defaults {
                myProjectType { }
            }

            println("non-declarative")
        """.trimIndent())

        withBuildScript("""
            myProjectType {
                myElements {
                    myElement("foo") { }
                }
            }

            println("non-declarative")
        """.trimIndent())

        build("kotlinDslAccessorsReport").apply {
            assertOutputContains("settings.gradle.kts:9:5: 'fun SharedModelDefaults.myProjectType(configureAction: Action<MyExtension>): Unit' is deprecated. Deprecated model type.")
            assertOutputContains("build.gradle.kts:1:1: 'fun Project.myProjectType(configure: Action<in MyExtension>): Unit' is deprecated. Deprecated model type.")
            assertOutputContains("build.gradle.kts:3:9: 'fun NamedDomainObjectContainer<MyElement>.myElement(name: String, configure: Action<in MyElement>): Unit' is deprecated. Deprecated element type.")

            assertOutputContains("""
            |    @Incubating
            |    @Suppress("deprecation")
            |    @Deprecated("Deprecated model type", level = DeprecationLevel.WARNING)
            |    fun org.gradle.api.Project.`myProjectType`(configure: Action<in com.example.MyExtension>) {
            """.trimMargin())

            assertOutputContains("""
            |    @Suppress("deprecation")
            |    @Deprecated("Deprecated element type", level = DeprecationLevel.WARNING)
            |    fun org.gradle.api.NamedDomainObjectContainer<com.example.MyElement>.`myElement`(
            """.trimMargin())
        }
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

    private fun withDclEnabledInGradleProperties() =
        withFile("gradle.properties").appendText("\n$DCL_ENABLED_PROPERTY_NAME=true\n")

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

    private fun withDeclarativePlugin() {
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

                @Suppress("deprecation")
                abstract class MyPlugin @Inject constructor(private val project: Project) : Plugin<Project> {
                    @get:SoftwareType(name = "myProjectType")
                    abstract val myProjectType: MyExtension

                    override fun apply(project: Project) = Unit
                }

                @Suppress("deprecation")
                @Deprecated("Deprecated model type", level = DeprecationLevel.WARNING)
                abstract class MyExtension {
                    abstract val myElements: NamedDomainObjectContainer<MyElement>
                }

                @Deprecated("Deprecated element type", level = DeprecationLevel.WARNING)
                abstract class MyElement(val elementName: String) : Named {
                    override fun getName() = elementName
                }
                """.trimIndent()
        )
    }

}
