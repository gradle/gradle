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

import org.gradle.kotlin.dsl.assignment.internal.KotlinDslAssignment.ASSIGNMENT_SYSTEM_PROPERTY
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class KotlinDslAssignmentIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `can use assignment for properties in init scripts when assignment overload is enabled`() {
        withAssignmentOverloadEnabled()
        val initScript = withInitScriptWithAssignment()

        // Expect
        build("-I", initScript.canonicalPath).apply {
            assertOutputContains("Init property value: Hello world")
        }
    }

    @Test
    fun `can use assignment for properties in settings scripts when assignment overload is enabled`() {
        withAssignmentOverloadEnabled()
        withSettingsWithAssignment()

        // Expect
        build().apply {
            assertOutputContains("Settings property value: Hello world")
        }
    }

    @Test
    fun `can use assignment for properties in build scripts when assignment overload is enabled`() {
        // Given
        withAssignmentOverloadEnabled()
        val outputFile = withBuildScriptWithAssignment()

        // When
        build("myTask")

        // Then
        assertEquals(
            "File 'build/myTask/hello-world.txt' content",
            "Hello world",
            outputFile.readText()
        )
    }

    @Test
    fun `cannot use assignment for properties in init scripts when assignment overload is not enabled`() {
        // Given
        val initScript = withInitScriptWithAssignment()

        // When
        val failure = buildAndFail("-I", initScript.canonicalPath)

        // Then
        failure.assertThatDescription(containsString("Val cannot be reassigned"))
    }

    @Test
    fun `cannot use assignment for properties in settings scripts when assignment overload is not enabled`() {
        // Given
        withSettingsWithAssignment()

        // When
        val failure = buildAndFail()

        // Then
        failure.assertThatDescription(containsString("Val cannot be reassigned"))
    }

    @Test
    fun `cannot use assignment for properties in build scripts when assignment overload is not enabled`() {
        // Given
        withBuildScriptWithAssignment()

        // When
        val failure = buildAndFail("myTask")

        // Then
        failure.assertThatDescription(containsString("Val cannot be reassigned"))
    }

    @Test
    fun `opt-in flag change is correctly detected for init scripts`() {
        // Given
        val initScript = withInitScriptWithAssignment()

        // When
        withAssignmentOverloadEnabled()

        // Then
        build("-I", initScript.canonicalPath)

        // When
        withAssignmentOverloadEnabled(enabled = false)
        val failure = buildAndFail("-I", initScript.canonicalPath)

        // Then
        failure.assertThatDescription(containsString("Val cannot be reassigned"))

        // When
        withAssignmentOverloadEnabled()

        // Then
        build("-I", initScript.canonicalPath)
    }

    @Test
    fun `opt-in flag change is correctly detected for settings scripts`() {
        // Given
        withSettingsWithAssignment()

        // When
        withAssignmentOverloadEnabled()

        // Then
        build()

        // When
        withAssignmentOverloadEnabled(enabled = false)
        val failure = buildAndFail()

        // Then
        failure.assertThatDescription(containsString("Val cannot be reassigned"))

        // When
        withAssignmentOverloadEnabled()

        // Then
        build()
    }

    @Test
    fun `opt-in flag change is correctly detected for build scripts`() {
        // Given
        withBuildScriptWithAssignment()

        // When
        withAssignmentOverloadEnabled()

        // Then
        build("myTask")

        // When
        withAssignmentOverloadEnabled(enabled = false)
        val failure = buildAndFail("myTask")

        // Then
        failure.assertThatDescription(containsString("Val cannot be reassigned"))

        // When
        withAssignmentOverloadEnabled()

        // Then
        build("myTask")
    }

    private
    fun withBuildScriptWithAssignment(): File {
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
        return File(outputFilePath)
    }

    private
    fun withSettingsWithAssignment() {
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
    }

    private
    fun withInitScriptWithAssignment(): File {
        return withFile(
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
    }

    private
    fun withAssignmentOverloadEnabled(enabled: Boolean = true) {
        withFile("gradle.properties", "systemProp.$ASSIGNMENT_SYSTEM_PROPERTY=$enabled")
    }
}
