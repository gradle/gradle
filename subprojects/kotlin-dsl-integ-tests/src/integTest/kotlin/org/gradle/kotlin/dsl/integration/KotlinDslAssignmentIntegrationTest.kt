/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class KotlinDslAssignmentIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `can use assignment for properties in init scripts`() {
        val initScript =
            withFile(
                "init.gradle.kts",
                """
                import org.gradle.kotlin.dsl.support.serviceOf
                data class Container(val property: Property<String>)
                fun newStringProperty(): Property<String> = gradle.serviceOf<ObjectFactory>().property(String::class.java)
                val container = Container(newStringProperty()).apply {
                    property = "Hello world"
                }
                println("Init property value: " + container.property.get())
                """.trimIndent()
            )

        build("-I", initScript.canonicalPath).apply {
            assertOutputContains("Init property value: Hello world")
        }
    }

    @Test
    fun `can use assignment for properties in settings scripts`() {
        withSettings("""
            import org.gradle.kotlin.dsl.support.serviceOf
            data class Container(val property: Property<String>)
            fun newStringProperty(): Property<String> = gradle.serviceOf<ObjectFactory>().property(String::class.java)
            val container = Container(newStringProperty()).apply {
                property = "Hello world"
            }
            println("Settings property value: " + container.property.get())
            """.trimIndent()
        )

        build().apply {
            assertOutputContains("Settings property value: Hello world")
        }
    }

    @Test
    fun `can use assignment for properties in build scripts`() {
        val outputFilePath = "${projectRoot.absolutePath}/build/myTask/hello-world.txt"
        withBuildScript("""
            abstract class MyTask : DefaultTask() {
                @get:Input
                abstract val input: Property<String>
                @get:OutputFile
                abstract val output: RegularFileProperty

                @TaskAction
                fun taskAction() {
                    output.asFile.get().writeText(input.get())
                }
            }

            tasks.register<MyTask>("myTask") {
                input = "Hello world"
                output = File("$outputFilePath")
            }
            """.trimIndent()
        )

        build("myTask")

        assertEquals(
            "File 'build/myTask/hello-world.txt' content",
            "Hello world",
            File(outputFilePath).readText()
        )
    }
}
