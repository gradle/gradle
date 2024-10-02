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
        withBuildScript(
            """
            plugins {
                `java`
            }

            configurations {
                configuration("myConfiguration") { }
            }

            sourceSets {
                sourceSet("mySourceSet") {
                    println("created my source set!")
                }
            }
        """
        )

        buildAndFail("dependencies") // no DCL support by default
            .assertHasErrorOutput("Unresolved reference: configuration")

        enableDclInGradleProperties()

        with(build("dependencies")) {
            assertTasksExecuted(":dependencies")
            assertOutputContains("myConfiguration\nNo dependencies")
        }

        with(build("compileMySourceSetJava", enableDclCliFlag)) {
            assertTasksSkipped(":compileMySourceSetJava")
            assertOutputContains("created my source set!")
        }
    }

    @Test
    fun `can use custom names for container element factories`() {
        withKotlinBuildSrc()
        withFile("buildSrc/src/main/kotlin/myPlugin.gradle.kts", """
            import org.gradle.declarative.dsl.model.annotations.ElementFactoryName

            abstract class MyExtension {
                abstract val myElements: NamedDomainObjectContainer<MyElement>
            }

            @ElementFactoryName("customName")
            abstract class MyElement(val elementName: String) : Named {
                override fun getName() = elementName
            }

            val myExtension = project.extensions.create("myExtension", MyExtension::class.java)

            tasks.register("printNames") {
                val names = myExtension.myElements.names
                doFirst {
                    println(names)
                }
            }
        """.trimIndent())

        withBuildScript("""
            plugins {
                id("myPlugin")
            }

            myExtension {
                myElements {
                    customName("one") { }
                    customName("two") { }
                }
            }
        """.trimIndent())

        buildAndFail("printNames") // no DCL support by default
            .assertHasErrorOutput("Unresolved reference: customName")

        with(build("printNames", enableDclCliFlag)) {
            assertTaskExecuted(":printNames")
            assertOutputContains("[one, two]")
        }
    }

    private fun enableDclInGradleProperties() =
        withFile("gradle.properties").appendText("\n$DCL_ENABLED_PROPERTY_NAME=true\n")

    private val enableDclCliFlag =
        "-D${DCL_ENABLED_PROPERTY_NAME}=true"
}
