/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.features.annotations.BindsProjectFeature
import org.gradle.features.annotations.BindsProjectType
import org.gradle.features.annotations.RegistersProjectFeatures
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectFeatureApplyAction
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectTypeApplyAction
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.kotlin.dsl.accessors.DCL_ENABLED_PROPERTY_NAME
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.junit.Test

class DclModelDefaultsScopingIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `feature accessor is not available at the top level of defaults`() {
        withPluginsWithTwoProjectTypesAndFeatures()
        enableDclInGradleProperties()

        withSettingsContent(
            """
            defaults {
                onlyForFirst {}
            }
            """.trimIndent()
        )

        buildAndFail("help").apply {
            assertHasErrorOutput("Script compilation error:")
            assertHasErrorOutput("onlyForFirst {}")
            assertHasErrorOutput("Unresolved reference")
        }
    }

    @Test
    fun `definition bound feature accessor is not available inside an unrelated project type block`() {
        withPluginsWithTwoProjectTypesAndFeatures()
        enableDclInGradleProperties()

        withSettingsContent(
            """
            defaults {
                secondProjectType {
                    onlyForFirst {}
                }
            }
            """.trimIndent()
        )

        buildAndFail("help").apply {
            assertHasErrorOutput("Script compilation error:")
            assertHasErrorOutput("onlyForFirst {}")
            assertHasErrorOutput("receiver type mismatch")
        }
    }

    @Test
    fun `build-model bound feature accessor is available on the project type with the matching build model`() {
        withPluginsWithTwoProjectTypesAndFeatures()
        enableDclInGradleProperties()

        withSettingsContent(
            """
            defaults {
                secondProjectType {
                    onlyForSecondBuildModel {}
                }
            }
            """.trimIndent()
        )

        build("help")
    }

    @Test
    fun `build-model bound feature accessor is not available on a project type with a different build model`() {
        withPluginsWithTwoProjectTypesAndFeatures()
        enableDclInGradleProperties()

        withSettingsContent(
            """
            defaults {
                firstProjectType {
                    onlyForSecondBuildModel {}
                }
            }
            """.trimIndent()
        )

        buildAndFail("help").apply {
            assertHasErrorOutput("Script compilation error:")
            assertHasErrorOutput("onlyForSecondBuildModel {}")
            assertHasErrorOutput("receiver type mismatch")
        }
    }

    @Test
    fun `definition bound feature accessor is available inside the project type it binds to`() {
        withPluginsWithTwoProjectTypesAndFeatures()
        enableDclInGradleProperties()

        withSettingsContent(
            """
            defaults {
                firstProjectType {
                    onlyForFirst {}
                }
            }
            """.trimIndent()
        )

        build("help")
    }

    private fun withPluginsWithTwoProjectTypesAndFeatures() {
        withFile(
            "build-logic/build.gradle.kts",
            """
                plugins {
                    id("java-gradle-plugin")
                    `kotlin-dsl`
                }

                repositories {
                    mavenCentral()
                }

                gradlePlugin {
                    plugins {
                        create("firstPlugin") {
                            id = "com.example.firstPlugin"
                            implementationClass = "com.example.FirstPlugin"
                        }
                        create("secondPlugin") {
                            id = "com.example.secondPlugin"
                            implementationClass = "com.example.SecondPlugin"
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
            "build-logic/src/main/kotlin/FirstPlugin.kt",
            """
                package com.example

                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import ${BindsProjectType::class.qualifiedName}
                import ${BindsProjectFeature::class.qualifiedName}
                import ${BuildModel::class.qualifiedName}
                import ${Definition::class.qualifiedName}
                import ${ProjectFeatureApplicationContext::class.qualifiedName}
                import ${ProjectFeatureApplyAction::class.qualifiedName}
                import ${ProjectFeatureBinding::class.qualifiedName}
                import ${ProjectFeatureBindingBuilder::class.qualifiedName}
                import ${ProjectTypeApplyAction::class.qualifiedName}
                import ${ProjectTypeBinding::class.qualifiedName}
                import ${ProjectTypeBindingBuilder::class.qualifiedName}

                interface FirstBuildModel : BuildModel
                interface FirstDefinition : ${Definition::class.simpleName}<FirstBuildModel>

                interface OnlyForFirstBuildModel : BuildModel
                interface OnlyForFirstDefinition : ${Definition::class.simpleName}<OnlyForFirstBuildModel>

                class FirstProjectTypeApplyAction : ${ProjectTypeApplyAction::class.simpleName}<FirstDefinition, FirstBuildModel> {
                    override fun apply(
                        context: ProjectFeatureApplicationContext,
                        definition: FirstDefinition,
                        buildModel: FirstBuildModel
                    ) {}
                }

                class OnlyForFirstApplyAction : ${ProjectFeatureApplyAction::class.simpleName}<OnlyForFirstDefinition, OnlyForFirstBuildModel, FirstDefinition> {
                    override fun apply(
                        context: ProjectFeatureApplicationContext,
                        definition: OnlyForFirstDefinition,
                        buildModel: OnlyForFirstBuildModel,
                        parentDefinition: FirstDefinition
                    ) {}
                }

                @${BindsProjectType::class.simpleName}(FirstPlugin.Binding::class)
                @${BindsProjectFeature::class.simpleName}(FirstPlugin.FeatureBinding::class)
                abstract class FirstPlugin : Plugin<Project> {

                    override fun apply(project: Project) = Unit

                    class Binding : ${ProjectTypeBinding::class.simpleName} {
                        override fun bind(builder: ProjectTypeBindingBuilder) {
                            builder.bindProjectType(
                                "firstProjectType",
                                FirstDefinition::class.java,
                                FirstProjectTypeApplyAction::class.java
                            )
                        }
                    }

                    class FeatureBinding : ${ProjectFeatureBinding::class.simpleName} {
                        override fun bind(builder: ProjectFeatureBindingBuilder) {
                            builder.bindProjectFeatureToDefinition(
                                "onlyForFirst",
                                OnlyForFirstDefinition::class.java,
                                FirstDefinition::class.java,
                                OnlyForFirstApplyAction::class.java
                            )
                        }
                    }
                }
            """.trimIndent()
        )

        withFile(
            "build-logic/src/main/kotlin/SecondPlugin.kt",
            """
                package com.example

                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import ${BindsProjectFeature::class.qualifiedName}
                import ${BindsProjectType::class.qualifiedName}
                import ${BuildModel::class.qualifiedName}
                import ${Definition::class.qualifiedName}
                import ${ProjectFeatureApplicationContext::class.qualifiedName}
                import ${ProjectFeatureApplyAction::class.qualifiedName}
                import ${ProjectFeatureBinding::class.qualifiedName}
                import ${ProjectFeatureBindingBuilder::class.qualifiedName}
                import ${ProjectTypeApplyAction::class.qualifiedName}
                import ${ProjectTypeBinding::class.qualifiedName}
                import ${ProjectTypeBindingBuilder::class.qualifiedName}

                interface SecondBuildModel : BuildModel
                interface SecondDefinition : ${Definition::class.simpleName}<SecondBuildModel>

                interface OnlyForSecondBuildModelBuildModel : BuildModel
                interface OnlyForSecondBuildModelDefinition : ${Definition::class.simpleName}<OnlyForSecondBuildModelBuildModel>

                class SecondProjectTypeApplyAction : ${ProjectTypeApplyAction::class.simpleName}<SecondDefinition, SecondBuildModel> {
                    override fun apply(context: ProjectFeatureApplicationContext, definition: SecondDefinition, buildModel: SecondBuildModel) {}
                }

                class OnlyForSecondBuildModelApplyAction : ${ProjectFeatureApplyAction::class.simpleName}<OnlyForSecondBuildModelDefinition, OnlyForSecondBuildModelBuildModel, Definition<SecondBuildModel>> {
                    override fun apply(
                        context: ProjectFeatureApplicationContext,
                        definition: OnlyForSecondBuildModelDefinition,
                        buildModel: OnlyForSecondBuildModelBuildModel,
                        parentDefinition: Definition<SecondBuildModel>
                    ) {}
                }

                @${BindsProjectType::class.simpleName}(SecondPlugin.Binding::class)
                @${BindsProjectFeature::class.simpleName}(SecondPlugin.FeatureBinding::class)
                abstract class SecondPlugin : Plugin<Project> {

                    override fun apply(project: Project) = Unit

                    class Binding : ${ProjectTypeBinding::class.simpleName} {
                        override fun bind(builder: ProjectTypeBindingBuilder) {
                            builder.bindProjectType(
                                "secondProjectType",
                                SecondDefinition::class.java,
                                SecondProjectTypeApplyAction::class.java
                            )
                        }
                    }

                    class FeatureBinding : ${ProjectFeatureBinding::class.simpleName} {
                        override fun bind(builder: ProjectFeatureBindingBuilder) {
                            builder.bindProjectFeatureToBuildModel(
                                "onlyForSecondBuildModel",
                                OnlyForSecondBuildModelDefinition::class.java,
                                SecondBuildModel::class.java,
                                OnlyForSecondBuildModelApplyAction::class.java
                            )
                        }
                    }
                }
            """.trimIndent()
        )

        withFile(
            "build-logic/src/main/kotlin/MyEcosystemPlugin.kt",
            """
                package com.example

                import org.gradle.api.Plugin
                import org.gradle.api.initialization.Settings
                import ${RegistersProjectFeatures::class.qualifiedName}

                @${RegistersProjectFeatures::class.simpleName}(FirstPlugin::class, SecondPlugin::class)
                class MyEcosystemPlugin : Plugin<Settings> {
                    override fun apply(settings: Settings) = Unit
                }
            """.trimIndent()
        )
    }

    private fun withSettingsContent(content: String) {
        withFile(
            "settings.gradle.kts",
            """
            pluginManagement {
                includeBuild("build-logic")
            }

            plugins {
                id("com.example.myEcosystemPlugin")
            }

            $content
            """.trimIndent()
        )
    }

    private fun enableDclInGradleProperties() =
        withFile("gradle.properties").appendText("\n$DCL_ENABLED_PROPERTY_NAME=true\n")
}
