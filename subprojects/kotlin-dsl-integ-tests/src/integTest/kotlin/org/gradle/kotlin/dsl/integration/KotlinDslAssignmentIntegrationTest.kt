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
        withAssignmentOverload()
        val initScript = withInitScriptWithAssignment()

        // Expect
        build("-I", initScript.absolutePath).apply {
            assertOutputContains("Init property value: Hello world")
            assertOutputContains("Kotlin DSL property assignment is an incubating feature.")
        }
    }

    @Test
    fun `can use assignment for properties in settings scripts when assignment overload is enabled`() {
        withAssignmentOverload()
        withSettingsWithAssignment()

        // Expect
        build().apply {
            assertOutputContains("Settings property value: Hello world")
            assertOutputContains("Kotlin DSL property assignment is an incubating feature.")
        }
    }

    @Test
    fun `can use assignment for properties in build scripts when assignment overload is enabled`() {
        // Given
        withAssignmentOverload()
        val outputFile = withBuildScriptWithAssignment()

        // When
        val result = build("myTask")

        // Then
        assertEquals(
            "File 'build/myTask/hello-world.txt' content",
            "Hello world",
            outputFile.readText()
        )
        result.assertOutputContains("Kotlin DSL property assignment is an incubating feature.")
    }

    @Test
    fun `cannot use assignment for properties in init scripts when assignment overload is not enabled`() {
        // Given
        val initScript = withInitScriptWithAssignment()

        // When
        val failure = buildAndFail("-I", initScript.absolutePath)

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
        withAssignmentOverload()

        // Then
        build("-I", initScript.absolutePath)

        // When
        withAssignmentOverload(enabled = false)
        val failure = buildAndFail("-I", initScript.absolutePath)

        // Then
        failure.assertThatDescription(containsString("Val cannot be reassigned"))

        // When
        withAssignmentOverload()

        // Then
        build("-I", initScript.absolutePath)
    }

    @Test
    fun `opt-in flag change is correctly detected for settings scripts`() {
        // Given
        withSettingsWithAssignment()

        // When
        withAssignmentOverload()

        // Then
        build()

        // When
        withAssignmentOverload(enabled = false)
        val failure = buildAndFail()

        // Then
        failure.assertThatDescription(containsString("Val cannot be reassigned"))

        // When
        withAssignmentOverload()

        // Then
        build()
    }

    @Test
    fun `opt-in flag change is correctly detected for build scripts`() {
        // Given
        withBuildScriptWithAssignment()

        // When
        withAssignmentOverload()

        // Then
        build("myTask")

        // When
        withAssignmentOverload(enabled = false)
        val failure = buildAndFail("myTask")

        // Then
        failure.assertThatDescription(containsString("Val cannot be reassigned"))

        // When
        withAssignmentOverload()

        // Then
        build("myTask")
    }

    @Test
    fun `assign operator compiles with all possible Property types`() {
        // Given
        withAssignmentOverload()
        withBuildScript("""
            abstract class MyTask : DefaultTask() {
                @get:Input
                abstract val input: Property<String>
                @get:Input
                abstract val mapInput: MapProperty<String, String>
                @get:Input
                abstract val setInput: SetProperty<String>
                @get:Input
                abstract val listInput: ListProperty<String>
                @get:InputDirectory
                abstract val dirInput: DirectoryProperty
                @get:Internal
                abstract val fileCollectionInput: ConfigurableFileCollection
                @get:OutputFile
                abstract val fileOutput: RegularFileProperty

                @TaskAction
                fun taskAction() {
                    fileOutput.asFile.get().writeText(input.get())
                }
            }

            tasks.register<MyTask>("myTask") {
                file("src").mkdirs()
                input = null
                input = "Hello world"
                input = provider { null }
                input = provider { "Hello" }
                mapInput = null
                mapInput = mapOf("a" to "b")
                mapInput = provider { null }
                mapInput = provider { mapOf("a" to "b") }
                setInput = null
                setInput = listOf("a")
                setInput = provider { null }
                setInput = provider { listOf("a") }
                listInput = null
                listInput = listOf("a")
                listInput = provider { null }
                listInput = provider { listOf("a") }
                dirInput = null as File?
                dirInput = null as Directory?
                dirInput = provider { null as File? }
                dirInput = provider { null as Directory? }
                dirInput = objects.directoryProperty()
                dirInput = objects.directoryProperty() as Provider<Directory>
                dirInput = file("src")
                dirInput = provider { file("src") }
                fileCollectionInput = files("a.txt")
                fileCollectionInput = files("a.txt") as FileCollection
                fileOutput = null as File?
                fileOutput = null as RegularFile?
                fileOutput = provider { null as File? }
                fileOutput = provider { null as RegularFile? }
                fileOutput = objects.fileProperty()
                fileOutput = objects.fileProperty() as Provider<RegularFile>
                fileOutput = file("build/myTask/hello.txt")
                fileOutput = provider { file("build/myTask/hello.txt") }
            }
            """.trimIndent()
        )

        // When, Then
        build("myTask")
    }

    private
    fun withBuildScriptWithAssignment(): File {
        val outputFilePath = "${projectRoot.absolutePath.replace("\\", "/")}/build/myTask/hello-world.txt"
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
    fun withAssignmentOverload(enabled: Boolean = true) {
        withFile("gradle.properties", "systemProp.$ASSIGNMENT_SYSTEM_PROPERTY=$enabled")
    }
}
