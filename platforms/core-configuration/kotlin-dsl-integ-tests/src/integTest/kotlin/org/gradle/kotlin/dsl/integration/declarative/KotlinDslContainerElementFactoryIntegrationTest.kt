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

import org.gradle.api.Namer
import org.gradle.features.registration.TaskRegistrar
import org.gradle.api.internal.AbstractNamedDomainObjectContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.features.annotations.BindsProjectType
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.internal.reflect.Instantiator
import org.junit.Test


class KotlinDslContainerElementFactoryIntegrationTest : AbstractDeclarativeKotlinIntegrationTest() {
    @Test
    fun `a named domain object container gets a synthetic element factory function`() {
        testKtsDefinitionWithDeclarativePlugin(withPluginsBlock = false, withCustomElementFactoryName = false)
    }

    @Test
    fun `can use custom project type names for element factories`() {
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
            .assertHasErrorOutput("Unresolved reference '$otherElementFactoryName'")

        with(build("printNames", enableDclCliFlag)) {
            assertTaskScheduled(":printNames")
            assertOutputContains("[one, two, four, three]")
        }

        // same as the CLI flag, putting the value to Gradle properties should enable DCL:
        enableDclInGradleProperties()

        with(build("printNames")) {
            assertTaskScheduled(":printNames")
            assertOutputContains("[one, two, four, three]")
        }
    }

    private fun withCustomSoftwarePluginWithContainer(customElementFactoryName: String? = null) {
        withEcosystemAndPluginBuildInBuildLogic()

        withEcosystemPluginRegisteringPluginClass()

        withFile(
            "build-logic/src/main/kotlin/MyPlugin.kt", """
                package com.example

                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import org.gradle.api.Named
                import org.gradle.api.NamedDomainObjectContainer
                import org.gradle.api.model.ObjectFactory
                import ${AbstractNamedDomainObjectContainer::class.java.name}
                import ${CollectionCallbackActionDecorator::class.java.name}
                import ${Instantiator::class.java.name}
                import ${Namer::class.java.name}
                import org.gradle.declarative.dsl.model.annotations.ElementFactoryName
                import javax.inject.Inject
                import ${BindsProjectType::class.java.name}
                import ${ProjectTypeBinding::class.java.name}
                import ${ProjectTypeBindingBuilder::class.java.name}
                import ${Definition::class.java.name}
                import ${BuildModel::class.java.name}
                import org.gradle.features.dsl.bindProjectType

                @${BindsProjectType::class.java.simpleName}(MyPlugin.Binding::class)
                abstract class MyPlugin @Inject constructor(private val project: Project) : Plugin<Project> {
                    class Binding : ${ProjectTypeBinding::class.java.simpleName} {
                        override fun bind(builder: ${ProjectTypeBindingBuilder::class.java.simpleName}) {
                            builder.bindProjectType("mySoftwareType") { definition: MyExtension, model ->
                                val services = objectFactory.newInstance(Services::class.java)
                                services.taskRegistrar.register("printNames") {
                                    val names = definition.myElements.names + definition.myElementsConcreteContainer.names
                                    doFirst {
                                        println(names)
                                    }
                                }
                            }.withUnsafeDefinition()
                        }

                        interface Services {
                            @get:Inject
                            val taskRegistrar: ${TaskRegistrar::class.java.name}
                        }
                    }

                    override fun apply(project: Project) { }
                }

                abstract class MyExtension @Inject constructor(objectFactory: ObjectFactory) : ${Definition::class.java.simpleName}<Model> {
                    abstract val myElements: NamedDomainObjectContainer<MyElement>
                    val myElementsConcreteContainer = MyContainerSubtype()
                }

                interface Model : ${BuildModel::class.java.simpleName} { }

                class MyContainerSubtype : AbstractNamedDomainObjectContainer<MyOtherElement>(
                    MyOtherElement::class.java,
                    object : Instantiator {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : Any> newInstance(type: Class<out T>, vararg parameters: Any?): T =
                            type.constructors.single().newInstance(*parameters) as T
                    },
                    Namer(MyOtherElement::getName),
                    CollectionCallbackActionDecorator.NOOP
                ) {
                    override fun doCreate(name: String): MyOtherElement = MyOtherElement(name)
                }

                ${if (customElementFactoryName != null) """@ElementFactoryName("$customElementFactoryName")""" else ""}
                abstract class MyElement(val elementName: String) : Named {
                    override fun getName() = elementName
                }

                ${if (customElementFactoryName != null) """@ElementFactoryName("$customElementFactoryName")""" else ""}
                class MyOtherElement(val elementName: String) : Named {
                    override fun getName() = elementName
                }
                """.trimIndent()
        )
    }
}
