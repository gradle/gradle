/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.instrumentation

import org.gradle.internal.hash.HashCode
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties


class InstrumentationMetadataPluginTest {
    @TempDir
    private
    lateinit var temporaryFolder: File

    private
    lateinit var projectRoot: File

    @BeforeEach
    fun setup() {
        projectRoot = File(temporaryFolder, "gradle")
        createProject()
    }

    @Test
    fun `should output instrumented-super-types properties with instrumented org-gradle super types`() {
        // When
        assertSucceeds()

        // Then
        val instrumentedSuperTypes = File(projectRoot, "distribution/build/instrumentation/instrumented-super-types.properties").readPropertiesFileToSorterMap()
        val instrumentedSuperTypesHash = File(projectRoot, "distribution/build/instrumentation/instrumented-super-types-hash.txt")
        val expectedSuperTypes = mapOf(
            "org/gradle/api/AbstractTask" to "org/gradle/api/Task",
            "org/gradle/api/DefaultTask" to "org/gradle/api/DefaultTask,org/gradle/api/Task",
            "org/gradle/api/Task" to "org/gradle/api/Task",
            "org/gradle/quality/Checkstyle" to "org/gradle/api/DefaultTask,org/gradle/api/Task",
            "org/gradle/quality/SourceTask" to "org/gradle/api/DefaultTask,org/gradle/api/Task"
        ).toSortedMap()
        assert(instrumentedSuperTypes == expectedSuperTypes) {
            "Expected instrumented-super-types.properties to be equal to:\n$expectedSuperTypes but was:\n$instrumentedSuperTypes"
        }
        val hashBytes = instrumentedSuperTypesHash.readBytes()
        assert(hashBytes.isNotEmpty()) {
            "Expected instrumented-super-types-hash.txt to not be empty"
        }
        assert(HashCode.fromBytes(hashBytes).toString() == "013efe852ca15532a60e3e133c783a81") {
            "Expected instrumented-super-types-hash.txt hash code to be: 013efe852ca15532a60e3e133c783a81, but was ${HashCode.fromBytes(hashBytes)}"
        }
    }

    @Test
    fun `should output empty instrumented-super-types properties file and empty hash file if none of types is instrumented`() {
        // Given
        // Override the build.gradle file to not write instrumented-classes.txt file
        File(projectRoot, "core/build.gradle").writeText("""
            plugins {
                id("java-library")
            }
        """)

        // When
        assertSucceeds()

        // Then
        val instrumentedSuperTypes = File(projectRoot, "distribution/build/instrumentation/instrumented-super-types.properties").readPropertiesFileToSorterMap()
        val instrumentedSuperTypesHash = File(projectRoot, "distribution/build/instrumentation/instrumented-super-types-hash.txt")
        assert(instrumentedSuperTypes.isEmpty()) {
            "Expected instrumented-super-types.properties to be empty but contained $instrumentedSuperTypes"
        }
        assert(instrumentedSuperTypesHash.length() == 0L) {
            "Expected instrumented-super-types-hash.txt to be empty but had length ${instrumentedSuperTypesHash.length()}"
        }
    }

    private
    fun File.readPropertiesFileToSorterMap() = this.inputStream()
        .use { Properties().apply { load(it) } }
        .map { it.key as String to it.value as String }
        .toMap()
        .toSortedMap()

    private
    fun runner() = GradleRunner.create()
        .withProjectDir(projectRoot)
        .withPluginClasspath()
        .forwardOutput()
        .withArguments(":distribution:findInstrumentedSuperTypes")

    private
    fun assertSucceeds() {
        runner().build()
    }

    private
    fun createProject() {
        projectRoot.mkdir()
        val coreDir = File(projectRoot, "core")
        val codeQualityDir = File(projectRoot, "code-quality")
        val distributionDir = File(projectRoot, "distribution")
        listOf(coreDir, codeQualityDir, distributionDir).forEach { it.mkdirs() }
        File(projectRoot, "settings.gradle").writeText("""
            rootProject.name = "instrumentation-metadata-test"
            include("core")
            include("code-quality")
            include("distribution")
        """)

        // Setup core project
        File(coreDir, "build.gradle").writeText("""
            plugins {
                id("java-library")
            }

            tasks.named("compileJava") {
                doLast {
                    // Simulate annotation processor output
                    file("build/classes/java/main/org/gradle/internal/instrumentation").mkdirs()
                    file("build/classes/java/main/org/gradle/internal/instrumentation/instrumented-classes.txt") << "org/gradle/api/Task\norg/gradle/api/DefaultTask"
                }
            }
        """)
        File(coreDir, "src/main/java/org/gradle/api").mkdirs()
        File(coreDir, "src/main/java/org/gradle/api").mkdirs()
        File(coreDir, "src/main/java/org/gradle/internal/instrumentation").mkdirs()
        File(coreDir, "src/main/java/org/gradle/api/Task.java").writeText("""
            package org.gradle.api;
            public interface Task {}
        """)
        File(coreDir, "src/main/java/org/gradle/api/AbstractTask.java").writeText("""
            package org.gradle.api;
            public class AbstractTask implements Task {}
        """)
        File(coreDir, "src/main/java/org/gradle/api/DefaultTask.java").writeText("""
            package org.gradle.api;
            public class DefaultTask extends AbstractTask {}
        """)

        // Setup code-quality project
        File(codeQualityDir, "build.gradle").writeText("""
            plugins {
                id("java-library")
            }
            dependencies {
                implementation(project(":core"))
            }
        """)
        File(codeQualityDir, "src/main/java/org/gradle/quality").mkdirs()
        File(codeQualityDir, "src/main/java/org/gradle/quality/SourceTask.java").writeText("""
            package org.gradle.quality;
            import org.gradle.api.DefaultTask;
            public class SourceTask extends DefaultTask {}
        """)
        File(codeQualityDir, "src/main/java/org/gradle/quality/Checkstyle.java").writeText("""
            package org.gradle.quality;
            public class Checkstyle extends SourceTask {}
        """)
        File(codeQualityDir, "src/main/java/org/gradle/quality/Unrelated.java").writeText("""
            package org.gradle.quality;
            public class Unrelated {}
        """)

        // Setup distribution project
        File(distributionDir, "build.gradle").writeText("""
            plugins {
                id("java-library")
                id("gradlebuild.instrumentation-metadata")
            }

            dependencies {
                implementation(project(":core"))
                implementation(project(":code-quality"))
            }

            instrumentationMetadata {
                classpathToInspect = createInstrumentationMetadataViewOf(configurations.runtimeClasspath)
                superTypesOutputFile = layout.buildDirectory.file("instrumentation/instrumented-super-types.properties")
                superTypesHashFile = layout.buildDirectory.file("instrumentation/instrumented-super-types-hash.txt")
            }
        """)
    }
}
