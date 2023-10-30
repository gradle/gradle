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

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


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
    fun `should output instrumented-super-types properties with instrumented org-gradle super types with stable order`() {
        // When
        assertSucceeds()

        // Then
        val instrumentedSuperTypes = File(projectRoot, "distribution/build/instrumentation/instrumented-super-types.properties").readLines()
        val expectedSuperTypes = listOf(
            "org/gradle/api/AbstractTask=org/gradle/api/Task",
            "org/gradle/api/DefaultTask=org/gradle/api/DefaultTask,org/gradle/api/Task",
            "org/gradle/api/Task=org/gradle/api/Task",
            "org/gradle/quality/Checkstyle=org/gradle/api/DefaultTask,org/gradle/api/Task",
            "org/gradle/quality/SourceTask=org/gradle/api/DefaultTask,org/gradle/api/Task"
        )
        assert(instrumentedSuperTypes == expectedSuperTypes) {
            "Expected instrumented-super-types.properties is not equal to expected:\nExpected:\n$expectedSuperTypes,\nActual:\n$instrumentedSuperTypes"
        }
    }

    @Test
    fun `should output merged upgraded properties json file in stable order`() {
        // When
        assertSucceeds()

        // Then
        val upgradedProperties = File(projectRoot, "distribution/build/instrumentation/upgraded-properties.json")
        upgradedProperties.assertHasContentEqualTo("[{\"containingType\":\"org.gradle.api.plugins.quality.Checkstyle\",\"propertyName\":\"maxErrors\"},{\"containingType\":\"org.gradle.api.plugins.quality.Checkstyle\",\"propertyName\":\"minErrors\"}," +
            "{\"containingType\":\"org.gradle.api.Task\",\"propertyName\":\"enabled\"},{\"containingType\":\"org.gradle.api.Task\",\"propertyName\":\"dependencies\"}]")
    }

    @Test
    fun `should not output files if none of types is instrumented`() {
        // Given
        // Override the build.gradle files to not write instrumented-classes.txt or upgraded-properties.json file
        File(projectRoot, "core/build.gradle").writeText("""
            plugins {
                id("java-library")
            }
        """)
        File(projectRoot, "code-quality/build.gradle").writeText("""
            plugins {
                id("java-library")
            }
            dependencies {
                implementation(project(":core"))
            }
        """)

        // When
        assertSucceeds()

        // Then
        val instrumentedSuperTypes = File(projectRoot, "distribution/build/instrumentation/instrumented-super-types.properties")
        val upgradedProperties = File(projectRoot, "distribution/build/instrumentation/upgraded-properties.json")
        instrumentedSuperTypes.assertDoesNotExist()
        upgradedProperties.assertDoesNotExist()
    }

    @Test
    fun `should not fail if metadata is not changed`() {
        // Given
        val upgradedProperties = File(projectRoot, "distribution/build/instrumentation/upgraded-properties.json")

        // When
        assertSucceeds()

        // Then, assert content from both project's metadata is in final metadata
        upgradedProperties.assertContentContains("org.gradle.api.Task")
        upgradedProperties.assertContentContains("org.gradle.api.plugins.quality.Checkstyle")

        // Given, change only some classes but not metadata
        File(projectRoot, "core/src/main/java/org/gradle/api/TaskAction.java").writeText("""
            package org.gradle.api;
            public interface TaskAction {}
        """)

        // When
        assertSucceeds()

        // Then, assert content from both project's metadata is still in final metadata
        upgradedProperties.assertContentContains("org.gradle.api.Task")
        upgradedProperties.assertContentContains("org.gradle.api.plugins.quality.Checkstyle")
    }

    private
    fun File.assertDoesNotExist() {
        assert(!this.exists()) {
            "Expected ${this.name} to not exist, but it did"
        }
    }

    private
    fun File.assertIsNotEmpty() {
        assert(this.exists()) {
            "Expected ${this.name} to exist, but it didn't"
        }
        assert(this.length() != 0L) {
            "Expected ${this.name} to not be empty but was"
        }
    }

    private
    fun File.assertHasContentEqualTo(content: String) {
        assert(this.readText() == content) {
            "Expected ${this.name} content:\nExpected:\n'$content',\nActual:\n'${this.readText()}'"
        }
    }

    private
    fun File.assertContentContains(text: String) {
        val fullContent = this.readText()
        assert(fullContent.contains(text)) {
            "Expected ${this.name} content to contain text: '$text', but it didn't. Full content:\n$fullContent"
        }
    }

    private
    fun runner() = GradleRunner.create()
        .withProjectDir(projectRoot)
        .withPluginClasspath()
        .forwardOutput()
        .withArguments(":distribution:mergeInstrumentedSuperTypes", ":distribution:mergeUpgradedProperties", "--stacktrace")

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
                    def instrumentedClassesFile = file("build/classes/java/main/org/gradle/internal/instrumentation/instrumented-classes.txt")
                    if (!instrumentedClassesFile.exists()) {
                        instrumentedClassesFile << "org/gradle/api/Task\norg/gradle/api/DefaultTask"
                    }
                    file("build/classes/java/main/META-INF/upgrades").mkdirs()
                    def upgradedPropertiesFile = file("build/classes/java/main/META-INF/upgrades/upgraded-properties.json")
                    if (!upgradedPropertiesFile.exists()) {
                        upgradedPropertiesFile << '[{"containingType":"org.gradle.api.Task","propertyName":"enabled"},{"containingType":"org.gradle.api.Task","propertyName":"dependencies"}]'
                    }
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
            tasks.named("compileJava") {
                doLast {
                    // Simulate annotation processor output
                    file("build/classes/java/main/META-INF/upgrades").mkdirs()
                    def upgradedPropertiesFile = file("build/classes/java/main/META-INF/upgrades/upgraded-properties.json")
                    if (!upgradedPropertiesFile.exists()) {
                        upgradedPropertiesFile << '[{"containingType":"org.gradle.api.plugins.quality.Checkstyle","propertyName":"maxErrors"},{"containingType":"org.gradle.api.plugins.quality.Checkstyle","propertyName":"minErrors"}]'
                    }
                }
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
                upgradedPropertiesFile = layout.buildDirectory.file("instrumentation/upgraded-properties.json")
            }
        """)
    }
}
