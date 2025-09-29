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

class OptInDclAccessorsIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `propagates opt-in annotations to DCL accessors`() {
        withEcosystemAndPluginBuildInBuildLogic()
        withEcosystemPluginRegisteringMyPlugin()
        withDeclarativePluginWithOptInRequirements()
        withDclEnabledInGradleProperties()

        file("settings.gradle.kts").appendText(
            "\n" + """
            import com.example.SomeExperimentalApi

            defaults {
                @OptIn(SomeExperimentalApi::class)
                myProjectType { }
            }
        """.trimIndent()
        )

        withBuildScript(
            """
            import com.example.SomeExperimentalApi

            @OptIn(SomeExperimentalApi::class)
            myProjectType {
                myElements {
                    myElement("foo") { }
                }
            }
        """.trimIndent()
        )

        //TODO: DCL cannot expose the project type accessors to a KTS precompiled script plugin yet, as it needs an ecosystem plugin to
        // register the models; so far we cannot test that the sources generated for the DCL accessors are valid by actually building against them.
        build("kotlinDslAccessorsReport").apply {
            assertNotOutput("w:")
            assertOutputContains(
                """
                |    @com.example.SomeExperimentalApi
                |    fun org.gradle.api.NamedDomainObjectContainer<com.example.MyElement>.`myElement`
                """.trimMargin()
            )

            assertOutputContains(
                """
                |    @com.example.SomeExperimentalApi
                |    fun org.gradle.api.Project.`myProjectType`(configure: Action<in com.example.MyExtension>) {
                """.trimMargin()
            )
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
                import org.jetbrains.kotlin.gradle.dsl.JvmTarget

                plugins {
                    id("java-gradle-plugin")
                    `kotlin-dsl`
                }

                kotlin {
                    compilerOptions {
                        jvmTarget = JvmTarget.JVM_1_8
                    }
                }

                tasks.compileJava {
                    targetCompatibility = "1.8"
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

    private fun withDeclarativePluginWithOptInRequirements() {
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

                @RequiresOptIn("Some Experimental API", RequiresOptIn.Level.ERROR)
                annotation class SomeExperimentalApi

                @OptIn(SomeExperimentalApi::class)
                @Suppress("deprecation")
                abstract class MyPlugin @Inject constructor(private val project: Project) : Plugin<Project> {
                    @get:SoftwareType(name = "myProjectType")
                    abstract val myProjectType: MyExtension

                    override fun apply(project: Project) = Unit
                }

                @SomeExperimentalApi
                @Suppress("deprecation")
                abstract class MyExtension {
                    abstract val myElements: NamedDomainObjectContainer<MyElement>
                }

                @SomeExperimentalApi
                abstract class MyElement(val elementName: String) : Named {
                    override fun getName() = elementName
                }
                """.trimIndent()
        )
    }
}
