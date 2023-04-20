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
    fun `can use assignment for properties in init scripts`() {
        val initScript = withInitScriptWithAssignment()

        // Expect
        build("-I", initScript.absolutePath).apply {
            assertOutputContains("Init property value: Hello world")
            assertOutputContains("Kotlin DSL property assignment is an incubating feature.")
        }
    }

    @Test
    fun `can use assignment for properties in settings scripts`() {
        withSettingsWithAssignment()

        // Expect
        build().apply {
            assertOutputContains("Settings property value: Hello world")
            assertOutputContains("Kotlin DSL property assignment is an incubating feature.")
        }
    }

    @Test
    fun `can use assignment for properties in build scripts`() {
        // Given
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
    fun `cannot use assignment for properties in init scripts when assignment overload is disabled`() {
        // Given
        withAssignmentOverload(disabled = true)
        val initScript = withInitScriptWithAssignment()

        // When
        val failure = buildAndFail("-I", initScript.absolutePath)

        // Then
        failure.assertThatDescription(containsString("Val cannot be reassigned"))
    }

    @Test
    fun `cannot use assignment for properties in settings scripts when assignment overload is disabled`() {
        // Given
        withAssignmentOverload(disabled = true)
        withSettingsWithAssignment()

        // When
        val failure = buildAndFail()

        // Then
        failure.assertThatDescription(containsString("Val cannot be reassigned"))
    }

    @Test
    fun `cannot use assignment for properties in build scripts when assignment overload is disabled`() {
        // Given
        withAssignmentOverload(disabled = true)
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

        // Then
        build("-I", initScript.absolutePath)

        // When
        withAssignmentOverload(disabled = true)
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

        // When, Then
        build()

        // When
        withAssignmentOverload(disabled = true)
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

        // When, Then
        build("myTask")

        // When
        withAssignmentOverload(disabled = true)
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

    @Test
    fun `doesn't report assignment is incubating feature when used by third party plugin`() {
        // Given
        val pluginDir = File(projectRoot, "plugin")
        File(pluginDir, "src/main/kotlin").mkdirs()
        File(pluginDir, "build.gradle.kts").writeText("""
            plugins {
                `kotlin-dsl`
            }
            repositories {
                mavenCentral()
            }
            """.trimIndent()
        )
        File(pluginDir, "settings.gradle.kts").writeText("rootProject.name = \"plugin\"")
        File(pluginDir, "src/main/kotlin/plugin-with-assignment.gradle.kts").writeText("""
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
                output = file("build/myTask/hello.txt")
            }
            """.trimIndent()
        )
        executer.inDirectory(pluginDir).withTasks("assemble").run()

        // When
        withAssignmentOverload(disabled = true)
        withBuildScript("""
            buildscript {
                dependencies { classpath(fileTree(mapOf("dir" to "plugin/build/libs", "include" to "*.jar"))) }
            }
            apply(plugin = "plugin-with-assignment")
            """.trimIndent()
        )
        val result = build("myTask")

        // Then
        assertEquals(
            "File 'build/myTask/hello.txt' content",
            "Hello world",
            File(projectRoot, "build/myTask/hello.txt").readText()
        )
        result.assertNotOutput("Kotlin DSL property assignment is an incubating feature.")
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
    fun withAssignmentOverload(disabled: Boolean = false) {
        withFile("gradle.properties", "systemProp.$ASSIGNMENT_SYSTEM_PROPERTY=${!disabled}")
    }
}
