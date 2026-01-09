/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.packaging

import gradlebuild.basics.repoRoot
import gradlebuild.packaging.tasks.GenerateClasspathModuleProperties
import gradlebuild.packaging.tasks.GenerateEmptyModuleProperties
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import java.io.File


object GradleDistributionSpecs {

    /**
     * The binary distribution containing everything needed to run Gradle (and nothing else).
     */
    fun Project.binDistributionSpec() = copySpec {
        val gradleScriptPath by configurations.getting
        val coreRuntimeClasspath by configurations.getting
        val generateCoreRuntimeModuleProperties by tasks.getting(GenerateClasspathModuleProperties::class)
        val runtimeClasspath by configurations.getting
        val generateRuntimeModuleProperties by tasks.getting(GenerateClasspathModuleProperties::class)
        val runtimeApiInfoJar by tasks.getting
        val runtimeApiInfoJarModuleProperties by tasks.getting(GenerateEmptyModuleProperties::class)
        val gradleApiKotlinExtensionsJar by tasks.getting
        val gradleApiKotlinExtensionsJarModuleProperties by tasks.getting(GenerateEmptyModuleProperties::class)
        val agentsRuntimeClasspath by configurations.getting
        val generateAgentsRuntimeModuleProperties by tasks.getting(GenerateClasspathModuleProperties::class)

        from("${repoRoot()}/LICENSE")
        from("src/toplevel")

        into("bin") {
            from(gradleScriptPath)
            filePermissions { unix("0755") }
        }

        val coreRuntimeProperties = generateCoreRuntimeModuleProperties.outputDir.asFileTree.elements
        val runtimeProperties = generateRuntimeModuleProperties.outputDir.asFileTree.elements

        into("lib") {
            from(runtimeApiInfoJar)
            from(runtimeApiInfoJarModuleProperties)

            from(gradleApiKotlinExtensionsJar)
            from(gradleApiKotlinExtensionsJarModuleProperties)
            from(coreRuntimeClasspath)
            from(coreRuntimeProperties)
            into("plugins") {
                from(runtimeClasspath - coreRuntimeClasspath)
                from(runtimeProperties.zip(coreRuntimeProperties) { runtime, coreRuntime ->
                    coreRuntime.mapTo(mutableSetOf<String>()) { it.asFile.name }.let { coreRuntimeNames ->
                        runtime.mapNotNull {
                            it.asFile.takeIf { !coreRuntimeNames.contains(it.name) }
                        }
                    }
                })
            }
            into("agents") {
                from(agentsRuntimeClasspath)
                from(generateAgentsRuntimeModuleProperties.outputDir)
            }
        }
    }

    /**
     * The binary distribution enriched with source files (including resources) and an offline version of Gradle's documentation (without samples).
     */
    fun Project.allDistributionSpec() = copySpec {
        val sourcesPath by configurations.getting
        val docsPath by configurations.getting

        with(binDistributionSpec())
        from(sourcesPath.incoming.artifactView { lenient(true) }.files) {
            eachFile {
                val subprojectFolder = file.containingSubprojectFolder(listOf("src", "main", "java").size + relativeSourcePath.segments.size)
                val leadingSegments = relativePath.segments.size - relativeSourcePath.segments.size
                @Suppress("SpreadOperator")
                relativePath = relativeSourcePath
                    .prepend("src", subprojectFolder.name)
                    .prepend(*(relativePath.segments.subArray(leadingSegments)))
                includeEmptyDirs = false
            }
        }
        into("docs") {
            from(docsPath)
            exclude("samples/**")
        }
    }

    /**
     * Offline version of the complete documentation of Gradle.
     */
    fun Project.docsDistributionSpec() = copySpec {
        val docsPath by configurations.getting

        from("${repoRoot()}/LICENSE")
        from("src/toplevel")
        into("docs") {
            from(docsPath)
        }
    }

    /**
     * All the source code of the project such that a complete build can be performed from the sources.
     */
    fun Project.srcDistributionSpec() = copySpec {
        from(repoRoot().file("gradlew")) {
            filePermissions { unix("0755") }
        }
        from(repoRoot()) {
            listOf(
                "build-logic-commons", "build-logic-commons/*",
                "build-logic", "build-logic/*",
                "build-logic-settings", "build-logic-settings/*",
                "subprojects/*", "platforms/*/*", "packaging/*",
                "testing/*",
            ).forEach {
                include("$it/*.gradle")
                include("$it/*.gradle.kts")
                include("$it/src/")
            }
            include("*.gradle.kts")
            include("gradle.properties")
            include("build-logic/gradle.properties")
            include("gradle/")
            include("wrapper/")
            include("gradlew.bat")
            include("version.txt")
            include("released-versions.json")
            exclude("**/.gradle/")
        }
    }

    private
    fun File.containingSubprojectFolder(relativePathLength: Int): File =
        if (relativePathLength == 0) this else this.parentFile.containingSubprojectFolder(relativePathLength - 1)

    private
    fun Array<String>.subArray(toIndex: Int) = this.sliceArray(0 until toIndex)
}
