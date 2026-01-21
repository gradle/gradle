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

package gradlebuild.refactoring

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.PrintWriter
import java.net.URI

/**
 * A task to assist in splitting a Gradle project into multiple subprojects.
 *
 * This task analyzes the dependencies of a given set of classes to determine which should be moved
 * to a new project and which dependencies that new project will require.
 *
 * This task scans the [projectClassesDirs] and [compileClasspathFiles] to index all available classes,
 * then computes the transitive closure of reachable classes starting from the [rootClasses]. Then, it
 * determines which discovered classes belong to the current project and therefore should be moved, and
 * which are dependencies. For each class to move, the task locates the corresponding Java source file
 * for the project classes to be moved, and for each dependency, determines the corresponding [ComponentIdentifier].
 */
@DisableCachingByDefault(because = "Unable to snapshot ComponentIdentifier") // Can be made cacheable after https://github.com/gradle/gradle/pull/36174
abstract class SplitProjectTask : DefaultTask() {

    /**
     * A comma-separated list of Fully Qualified Class Names (FQCNs) to begin searching from to
     * discover which classes should be moved. All classes that these classes transitively depend
     * on within [projectClassesDirs] will be identified for moving.
     */
    @get:Input
    @get:Option(option = "classes", description = "Comma-separated list of FQCNs to split")
    abstract val rootClasses: Property<String>

    /**
     * The destination platform of the new project.
     */
    @get:Internal
    @get:Option(option = "to-platform", description = "The target platform for the new project")
    abstract val toPlatform: Property<String>

    /**
     * The name of the new project within the destination platform.
     */
    @get:Internal
    @get:Option(option = "to-project", description = "The name of the new project")
    abstract val toProject: Property<String>

    /**
     * The root directory of the Gradle build. The destination platform and
     * project are resolved relative to this directory.
     */
    @get:Internal
    abstract val rootProjectDirectory: DirectoryProperty

    /**
     * The destination directory for the new split project.
     */
    @get:Input
    val targetProjectDirectory: Provider<Directory>
        get() = rootProjectDirectory.dir("platforms").map { it.dir(toPlatform.get()).dir(toProject.get()) }

    /**
     * The directories containing the compiled class files of the current project.
     */
    @get:InputFiles
    @get:Classpath
    abstract val projectClassesDirs: ConfigurableFileCollection

    /**
     * The list of artifact files (JARs or directories) in the compile classpath.
     * This list must correspond 1-to-1 with [compileClasspathComponentIds].
     */
    @get:InputFiles
    @get:Classpath
    abstract val compileClasspathFiles: ListProperty<File>

    /**
     * A list of component identifiers for the artifacts in the compile classpath.
     * This list must correspond 1-to-1 with [compileClasspathFiles].
     */
    @get:Internal // Can be declared input after https://github.com/gradle/gradle/pull/36174
    abstract val compileClasspathComponentIds: ListProperty<ComponentIdentifier>

    /**
     * The source directories of the current project containing Java source files.
     * Used to find the .java source files corresponding to the classes to be moved.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val javaSourceDirectories: ConfigurableFileCollection

    /**
     * The root directory of the current project being split.
     */
    @get:Internal
    abstract val currentProjectDirectory: DirectoryProperty

    /**
     * The test source directories of the current project being split.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSourceDirectories: ConfigurableFileCollection

    @TaskAction
    fun split() {
        val targetDir = targetProjectDirectory.get().asFile
        if (targetDir.exists()) {
            throw GradleException("Target project directory already exists: ${targetDir.absolutePath}")
        }

        val rootClassNames = rootClasses.get().split(",").map { it.trim() }.toSet()
        val result = DependencyAnalyzer().analyze(
            projectClassesDirs.files,
            compileClasspathFiles.get().toSet(),
            rootClassNames
        )

        val sourceFileMoves = mutableSetOf<Pair<File, String>>()

        // Find all source files to move to the new project
        discoverProjectSourceFiles(result, sourceFileMoves)

        // Find all tests to move to the new project
        discoverRelevantTests(result, sourceFileMoves)

        // Move the source files to the new project
        sourceFileMoves.forEach { (from, relativePath) ->
            val to = File(targetDir, relativePath)
            to.parentFile.mkdirs()
            if (!from.renameTo(to)) {
                throw GradleException("Failed to move ${from.absolutePath} to ${to.absolutePath}")
            }
        }

        // Generate the project build file
        File(targetDir, "build.gradle.kts").printWriter().use { out ->
            out.generateBuildFile(result)
        }

        println("Successfully generated project ${toProject.get()} at ${targetDir.asClickableFileUrl()}")
        println()
        println("The following manual steps must still be performed:")
        println("- Add 'subproject(\"${toProject.get()}\")' to ${rootProjectDirectory.file("settings.gradle.kts").get().asFile.asClickableFileUrl()}")
        println("- Update the the current project to depend on `projects.${toProjectAccessorName(":${toProject.get()}")}`: ${currentProjectDirectory.file("build.gradle.kts").get().asFile.asClickableFileUrl()}")
        println("- Run `./gradlew :generateSubprojectsInfo`")
        println("- Run `./gradlew projectHealth --continue` and resolve any reported dependency declaration issues")
        println("- Sync the build and commit the idea.xml file changes")
    }

    /**
     * For each discovered project class, find the corresponding source file
     */
    private fun discoverProjectSourceFiles(
        result: AnalysisResult,
        sourceFileMoves: MutableSet<Pair<File, String>>
    ) {
        val sourceDirs = javaSourceDirectories.files
        val missingSources = mutableListOf<String>()

        result.projectClasses.forEach { className ->
            val relativePath = toRelativeJavaSourceFilePath(className)

            var found = false
            for (sourceDir in sourceDirs) {
                val sourceFile = File(sourceDir, relativePath)
                if (sourceFile.exists()) {
                    val projectRelativePath = "src/main/java/$relativePath"
                    sourceFileMoves.add(sourceFile to projectRelativePath)
                    found = true
                    break
                }
            }

            if (!found) {
                missingSources.add(className)
            }
        }

        if (missingSources.isNotEmpty()) {
            throw GradleException("Could not find source files for the following classes:\n" + missingSources.joinToString("\n"))
        }
    }

    /**
     * Use naming heuristics to find test files corresponding to the classes being moved.
     */
    private fun discoverRelevantTests(
        result: AnalysisResult,
        sourceFileMoves: MutableSet<Pair<File, String>>
    ) {
        val testSuffixes = listOf("Test", "Spec", "IntegTest", "IntegSpec", "IntegrationTest", "IntegrationSpec")
        val testExtensions = listOf("java", "groovy")
        val testSourceDirs = testSourceDirectories.files
        val currentProjectDir = currentProjectDirectory.get().asFile

        val topLevelClasses = result.projectClasses.map {
            if (it.contains("$")) it.substringBefore("$") else it
        }.toSet()

        topLevelClasses.forEach { className ->
            val packagePath = className.substringBeforeLast('.', "").replace('.', '/')
            val simpleName = className.substringAfterLast('.')

            testSuffixes.forEach { suffix ->
                testExtensions.forEach { ext ->
                    val testFileName = "$simpleName$suffix.$ext"
                    val relativePath = if (packagePath.isNotEmpty()) "$packagePath/$testFileName" else testFileName
                    testSourceDirs.forEach { sourceDir ->
                        val sourceFile = File(sourceDir, relativePath)
                        if (sourceFile.exists()) {
                            val relativeSourceRoot = sourceDir.relativeTo(currentProjectDir).path
                            val relativePath = "$relativeSourceRoot/$relativePath"
                            sourceFileMoves.add(sourceFile to relativePath)
                        }
                    }
                }
            }
        }
    }

    /**
     * Generates a build file for the new project containing the dependencies of the
     * classes being moved.
     */
    private fun PrintWriter.generateBuildFile(result: AnalysisResult) {
        val (foundApiComponents, foundImplComponents) = resolveDependencyComponents(result)
        val apiDeps = sortDependencies(foundApiComponents.map {
            toBuildscriptDependencyDeclaration(it, "api")
        })
        val implDeps = sortDependencies(foundImplComponents.map {
            toBuildscriptDependencyDeclaration(it, "implementation")
        })

        println("plugins {")
        println("    id(\"gradlebuild.distribution.api-java\")")
        println("}")
        println()
        println("dependencies {")
        apiDeps.forEach { println("    $it") }
        if (apiDeps.isNotEmpty() && implDeps.isNotEmpty()) {
            println()
        }
        implDeps.forEach { println("    $it") }
        println("}")
    }

    /**
     * For each dependency artifact, find the corresponding ComponentIdentifier
     * which produces that artifact
     */
    private fun resolveDependencyComponents(
        result: AnalysisResult
    ): Pair<MutableSet<ComponentIdentifier>, MutableSet<ComponentIdentifier>> {
        val componentIds = compileClasspathComponentIds.get()
        val artifactFilesToComponentIds: Map<File, ComponentIdentifier> = compileClasspathFiles.get().zip(componentIds).toMap()

        val missingComponents = mutableListOf<String>()
        fun resolveComponents(dependencyFiles: Set<File>, dependencyComponentIds: MutableSet<ComponentIdentifier>) {
            dependencyFiles.forEach { file ->
                val id = artifactFilesToComponentIds[file]
                if (id != null) {
                    dependencyComponentIds.add(id)
                } else {
                    missingComponents.add(file.absolutePath)
                }
            }
        }

        val foundApiComponents = mutableSetOf<ComponentIdentifier>()
        val foundImplComponents = mutableSetOf<ComponentIdentifier>()
        resolveComponents(result.apiDependencyRoots, foundApiComponents)
        resolveComponents(result.implementationDependencyRoots, foundImplComponents)

        if (missingComponents.isNotEmpty()) {
            throw GradleException("Could not find Component IDs for the following dependency files:\n" + missingComponents.joinToString("\n"))
        }
        return foundApiComponents to foundImplComponents
    }

    /**
     * Sorts the given list of declared dependencies by project dependency first, then alphabetically.
     */
    private fun sortDependencies(deps: List<String>): List<String> {
        return deps.sortedWith { a, b ->
            val aIsProject = a.contains("projects.")
            val bIsProject = b.contains("projects.")
            when {
                aIsProject && !bIsProject -> -1
                !aIsProject && bIsProject -> 1
                else -> a.compareTo(b)
            }
        }
    }

    /**
     * Given a [ComponentIdentifier] and a configuration name, returns the corresponding
     * dependency declaration which declares a dependency on that component.
     */
    private fun toBuildscriptDependencyDeclaration(id: ComponentIdentifier, configuration: String): String {
        return when (id) {
            is ProjectComponentIdentifier -> {
                val accessor = toProjectAccessorName(id.projectPath)
                "$configuration(projects.$accessor)"
            }
            is ModuleComponentIdentifier -> {
                "$configuration(\"${id.group}:${id.module}\")"
            }
            else -> throw GradleException("Unknown dependency type: ${id::class.java.name}")
        }
    }

    /**
     * Converts a project path to a typesafe project accessor name.
     */
    private fun toProjectAccessorName(projectPath: String): String =
        projectPath.removePrefix(":").split(":").joinToString(".") { part ->
            part.split("-", "_").mapIndexed { index, s ->
                if (index == 0) s else s.replaceFirstChar { it.uppercase() }
            }.joinToString("")
        }

    /**
     * Given a class name, returns the corresponding relative path to a Java source file
     * producing that class.
     */
    private fun toRelativeJavaSourceFilePath(className: String): String {
        val topLevelClassName = if (className.contains("$")) {
            className.substringBefore("$")
        } else {
            className
        }
        return topLevelClassName.replace('.', '/') + ".java"
    }

    fun File.asClickableFileUrl(): String {
        return URI("file", "", toURI().getPath(), null, null).toASCIIString()
    }

}
