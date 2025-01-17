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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.accessors.DCL_ENABLED_PROPERTY_NAME
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.junit.Test


class KotlinDslContainerElementFactoryIntegrationTest : AbstractKotlinIntegrationTest() {
    @Test
    fun `a named domain object container gets a synthetic element factory function`() {
        testKtsDefinitionWithDeclarativePlugin(withPluginsBlock = false, withCustomElementFactoryName = false)
    }

    @Test
    fun `can use custom software type names for element factories`() {
        testKtsDefinitionWithDeclarativePlugin(withPluginsBlock = false, withCustomElementFactoryName = true)
    }

    @Test
    fun `can use the declarative DSL with plugins block if the plugin is software-type-based`() {
        testKtsDefinitionWithDeclarativePlugin(withPluginsBlock = true, withCustomElementFactoryName = true)
    }

    private fun testKtsDefinitionWithDeclarativePlugin(withPluginsBlock: Boolean, withCustomElementFactoryName: Boolean) {
        val elementFactoryName = if (withCustomElementFactoryName) "customName" else "myElement"
        val otherElementFactoryName = if (withCustomElementFactoryName) "customName" else "myOtherElement"

        withCustomSoftwarePluginWithContainer(if (withCustomElementFactoryName) otherElementFactoryName else null)

        withBuildScript(
            """
            ${if (withPluginsBlock) "plugins { id(\"com.example.myPlugin\") }" else ""}

            mySoftwareType {
                myElements {
                    $elementFactoryName("one") { }
                    $elementFactoryName("two") { }
                }
                myElementsConcreteContainer {
                    $otherElementFactoryName("three") { }
                    $otherElementFactoryName("four") { }
                }
            }

            println("make sure this is not a declarative file")
            """.trimIndent()
        )

        buildAndFail("printNames") // no DCL support by default
            .assertHasErrorOutput("Unresolved reference: $otherElementFactoryName")

        with(build("printNames", enableDclCliFlag)) {
            assertTaskExecuted(":printNames")
            assertOutputContains("[one, two, four, three]")
        }

        // same as the CLI flag, putting the value to Gradle properties should enable DCL:
        enableDclInGradleProperties()

        with(build("printNames")) {
            assertTaskExecuted(":printNames")
            assertOutputContains("[one, two, four, three]")
        }
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

    private fun withCustomSoftwarePluginWithContainer(customElementFactoryName: String? = null) {
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
                import org.gradle.api.model.ObjectFactory
                import org.gradle.declarative.dsl.model.annotations.ElementFactoryName
                import javax.inject.Inject

                abstract class MyPlugin @Inject constructor(private val project: Project) : Plugin<Project> {
                    @get:SoftwareType(name = "mySoftwareType")
                    abstract val mySoftwareType: MyExtension

                    override fun apply(project: Project) {
                        project.tasks.register("printNames") {
                            val names = mySoftwareType.myElements.names + mySoftwareType.myElementsConcreteContainer.names
                            doFirst {
                                println(names)
                            }
                        }
                    }
                }

                abstract class MyExtension @Inject constructor(objectFactory: ObjectFactory) {
                    abstract val myElements: NamedDomainObjectContainer<MyElement>
                    val myElementsConcreteContainer = MyContainerSubtype(objectFactory.domainObjectContainer(MyOtherElement::class.java))
                }

                class MyContainerSubtype @Inject constructor(backingContainer: NamedDomainObjectContainer<MyOtherElement>)
                    : NamedDomainObjectContainer<MyOtherElement> by backingContainer

                ${if (customElementFactoryName != null) """@ElementFactoryName("$customElementFactoryName")""" else ""}
                abstract class MyElement(val elementName: String) : Named {
                    override fun getName() = elementName
                }

                ${if (customElementFactoryName != null) """@ElementFactoryName("$customElementFactoryName")""" else ""}
                abstract class MyOtherElement(val elementName: String) : Named {
                    override fun getName() = elementName
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

    private val enableDclCliFlag =
        "-D${DCL_ENABLED_PROPERTY_NAME}=true"
}
