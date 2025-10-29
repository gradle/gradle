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

import org.gradle.api.internal.plugins.BindsProjectFeature
import org.gradle.api.internal.plugins.BindsProjectType
import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.ProjectFeatureBindingBuilder
import org.gradle.api.internal.plugins.ProjectFeatureBindingRegistration
import org.gradle.api.internal.plugins.ProjectTypeBindingBuilder
import org.gradle.api.internal.plugins.ProjectTypeBindingRegistration
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
            myProjectType {
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
                    .contains("FailuresInResolution(errors=[ResolutionError(element=println(\"not declarative anymore!\")")
            }
        }
    }

    @Test
    fun `has usable accessors for features targeting nested blocks`() {
        withCustomSoftwarePluginWithContainer()
        enableDclInGradleProperties()

        withBuildScript(
            """
            myProjectType {
                myFeature {
                    myNestedFeature {
                        myNestedFeature { }
                    }
                }
            }

            println("not declarative anymore!")
            """.trimIndent()
        )

        with(build(":kotlinDslAccessorsReport")) {
            assertOutputContains("not declarative anymore!")
            assertOutputContains("apply myNestedFeature to MyFeatureDefinition_Decorated")
            assertOutputContains("apply myNestedFeature to MyNestedFeatureDefinition_Decorated")

            assertOutputContains(
                """
                |    @Incubating
                |    fun com.example.MyExtension.`myFeature`(configure: Action<in com.example.MyFeatureDefinition>) {
                |        applyProjectType(this, "myFeature", configure)
                |    }
                """.trimMargin()
            )

            assertOutputContains(
                """
                |    @Incubating
                |    fun org.gradle.api.internal.plugins.Definition<out com.example.MyFeatureBuildModel>.`myNestedFeature`(configure: Action<in com.example.MyNestedFeatureDefinition>) {
                |        applyProjectType(this, "myNestedFeature", configure)
                |    }
                """.trimMargin()
            )
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
                import ${BindsProjectType::class.qualifiedName}
                import ${BindsProjectFeature::class.qualifiedName}
                import ${Definition::class.qualifiedName}
                import ${BuildModel::class.qualifiedName}
                import ${ProjectTypeBindingRegistration::class.qualifiedName}
                import ${ProjectTypeBindingBuilder::class.qualifiedName}
                import ${ProjectFeatureBindingRegistration::class.qualifiedName}
                import ${ProjectFeatureBindingBuilder::class.qualifiedName}
                import org.gradle.api.internal.plugins.features.dsl.bindProjectType
                import org.gradle.api.internal.plugins.features.dsl.bindProjectFeatureToDefinition
                import org.gradle.api.internal.plugins.features.dsl.bindProjectFeatureToBuildModel

                @${BindsProjectType::class.simpleName}(MyPlugin.Binding::class)
                @${BindsProjectFeature::class.simpleName}(MyPlugin.FeatureBinding::class)
                abstract class MyPlugin : Plugin<Project> {

                    override fun apply(project: Project) = Unit

                    class Binding : ${ProjectTypeBindingRegistration::class.simpleName} {
                        override fun register(builder: ProjectTypeBindingBuilder) {
                            builder.bindProjectType("myProjectType") { definition: MyExtension, model ->
                                project.tasks.register("printNames") {
                                    val names = definition.myElements.names
                                    doFirst {
                                        println(names)
                                    }
                                }
                            }
                        }
                    }
                    class FeatureBinding : ${ProjectFeatureBindingRegistration::class.simpleName} {
                        override fun register(builder: ProjectFeatureBindingBuilder) {
                            builder.bindProjectFeatureToDefinition(
                                "myFeature",
                                MyFeatureDefinition::class,
                                MyExtension::class
                            ) { definition, buildModel, target ->
                                println("apply myFeature")
                            }

                            builder.bindProjectFeatureToBuildModel(
                                "myNestedFeature",
                                MyNestedFeatureDefinition::class,
                                MyFeatureBuildModel::class
                            ) { definition, buildModel, target ->
                                println("apply myNestedFeature to ${'$'}{target::class.simpleName}")
                            }
                        }
                    }
                }

                interface MyExtensionBuildModel : BuildModel
                abstract class MyExtension : ${Definition::class.simpleName}<MyExtensionBuildModel> {
                    abstract val myElements: NamedDomainObjectContainer<MyElement>
                }

                interface MyFeatureBuildModel : BuildModel

                abstract class MyFeatureDefinition : ${Definition::class.simpleName}<MyFeatureBuildModel>

                abstract class MyNestedFeatureDefinition : MyFeatureDefinition()

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
