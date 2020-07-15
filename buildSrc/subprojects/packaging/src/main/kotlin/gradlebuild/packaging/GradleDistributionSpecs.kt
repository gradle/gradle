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
        val runtimeClasspath by configurations.getting
        val runtimeApiInfoJar by tasks.getting

        from("$rootDir/LICENSE")
        from("src/toplevel")

        into("bin") {
            from(gradleScriptPath)
            fileMode = Integer.parseInt("0755", 8)
        }

        into("lib") {
            from(runtimeApiInfoJar)
            from(coreRuntimeClasspath)
            into("plugins") {
                from(runtimeClasspath - coreRuntimeClasspath)
            }
        }
    }

    /**
     * The binary distribution enriched with the sources for the classes and an offline version of Gradle's documentation (without samples).
     */
    fun Project.allDistributionSpec() = copySpec {
        val sourcesPath by configurations.getting
        val docsPath by configurations.getting

        with(binDistributionSpec())
        from(sourcesPath.incoming.artifactView { lenient(true) }.files) {
            eachFile {
                val subprojectFolder = file.containingSubprojectFolder(listOf("src", "main", "java").size + relativeSourcePath.segments.size)
                val leadingSegments = relativePath.segments.size - relativeSourcePath.segments.size
                relativePath = relativeSourcePath.prepend("src", subprojectFolder.name).prepend(*(relativePath.segments.subArray(leadingSegments)))
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

        from("$rootDir/LICENSE")
        from("src/toplevel")
        into("docs") {
            from(docsPath)
        }
    }

    /**
     * All the source code of the project such that a complete build can be performed from the sources.
     */
    fun Project.srcDistributionSpec() = copySpec {
        from(rootProject.file("gradlew")) {
            fileMode = Integer.parseInt("0755", 8)
        }
        from(rootProject.projectDir) {
            listOf("buildSrc", "buildSrc/subprojects/*", "subprojects/*").forEach {
                include("$it/*.gradle")
                include("$it/*.gradle.kts")
                include("$it/src/")
            }
            include("gradle.properties")
            include("buildSrc/gradle.properties")
            include("config/")
            include("gradle/")
            include("src/")
            include("*.gradle")
            include("*.gradle.kts")
            include("wrapper/")
            include("gradlew.bat")
            include("version.txt")
            include("released-versions.json")
            exclude("**/.gradle/")
        }
    }

    private
    fun File.containingSubprojectFolder(relativePathLenght: Int): File =
        if (relativePathLenght == 0) this else this.parentFile.containingSubprojectFolder(relativePathLenght - 1)

    private
    fun Array<String>.subArray(toIndex: Int) = listOf(*this).subList(0, toIndex).toTypedArray()
}
