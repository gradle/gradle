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

import org.gradle.features.registration.TaskRegistrar
import org.gradle.features.annotations.BindsProjectType
import org.gradle.features.binding.BuildModel
import org.gradle.features.binding.Definition
import org.gradle.features.binding.ProjectTypeBinding
import org.gradle.features.binding.ProjectTypeBindingBuilder
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.junit.Assert
import org.junit.Test


class KotlinDslDeclarativeNestedModelIntegrationTest : AbstractDeclarativeKotlinIntegrationTest() {
    @Test
    fun `nested models can be accessed via synthetic functions in Kotlin DSL`() {
        withEcosystemAndPluginBuildInBuildLogic()
        withEcosystemPluginRegisteringPluginClass()

        enableDclInGradleProperties()

        withPluginSourceFileInBuildLogic(
            "MyPlugin.kt", """
                package com.example

                import org.gradle.api.Plugin
                import org.gradle.api.Project
                import org.gradle.api.Named
                import ${Nested::class.java.name}
                import ${Property::class.java.name}
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
                                services.taskRegistrar.register("printFoo") {
                                    val nestedFoo = definition.myNested.foo
                                    val moreNestedFoo = definition.myNested.moreNested.foo
                                    doFirst {
                                        println(nestedFoo.get() + ", " + moreNestedFoo.get())
                                    }
                                }
                            }
                        }

                        interface Services {
                            @get:Inject
                            val taskRegistrar: ${TaskRegistrar::class.java.name}
                        }
                    }

                    override fun apply(project: Project) { }
                }

                interface MyExtension : ${Definition::class.java.simpleName}<Model> {
                    @get:Nested
                    val myNested: MyNested
                }

                interface MyNested {
                    val foo: Property<String>

                    @get:Nested
                    val moreNested: MoreNested
                }

                interface MoreNested {
                    val foo: Property<String>
                }

                interface Model : ${BuildModel::class.java.simpleName} { }

                """.trimIndent()
        )

        withBuildScript(
            """
                mySoftwareType {
                    myNested {
                        foo = "bar"
                        moreNested {
                            foo = "baz"
                        }
                    }
                }

                println("this is not a declarative build file")
            """.trimIndent()
        )

        with(build(":printFoo", "kotlinDslAccessorsReport")) {
            assertOutputContains("bar, baz")
            assertOutputContains("fun com.example.MyExtension.`myNested`(configure: Action<com.example.MyNested>)")
            assertOutputContains("fun com.example.MyNested.`moreNested`(configure: Action<com.example.MoreNested>)")
            Assert.assertTrue("no other nested models than the two in the definition should be exposed", output.lines().count { it.contains("nested model") } == 2)
        }
    }
}
