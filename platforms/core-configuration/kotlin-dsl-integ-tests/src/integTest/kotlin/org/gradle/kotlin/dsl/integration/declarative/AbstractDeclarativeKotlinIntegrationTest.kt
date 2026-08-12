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

package org.gradle.kotlin.dsl.integration.declarative

import org.gradle.features.annotations.RegistersProjectFeatures
import org.gradle.kotlin.dsl.accessors.DCL_ENABLED_PROPERTY_NAME
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

abstract class AbstractDeclarativeKotlinIntegrationTest : AbstractKotlinIntegrationTest() {
    protected val enableDclCliFlag =
        "-D${DCL_ENABLED_PROPERTY_NAME}=true"

    protected fun enableDclInGradleProperties() =
        withFile("gradle.properties").appendText("\n$DCL_ENABLED_PROPERTY_NAME=true\n")

    protected fun withPluginSourceFileInBuildLogic(fileName: String, content: String) =
        withFile("build-logic/src/main/kotlin/$fileName", content)

    protected fun withEcosystemPluginRegisteringPluginClass(pluginClassName: String = "com.example.MyPlugin") {
        withFile(
            "build-logic/src/main/kotlin/MyEcosystemPlugin.kt", """
                package com.example

                import org.gradle.api.Plugin
                import org.gradle.api.initialization.Settings
                import ${RegistersProjectFeatures::class.qualifiedName}

                @${RegistersProjectFeatures::class.simpleName}($pluginClassName::class)
                class MyEcosystemPlugin : Plugin<Settings> {
                    override fun apply(settings: Settings) = Unit
                }
            """.trimIndent()
        )
    }

    protected fun withEcosystemAndPluginBuildInBuildLogic() {
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

}
