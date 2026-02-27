/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.buildutils.runtimes

import com.google.gson.Gson
import gradlebuild.runtimes.TargetRuntime
import gradlebuild.runtimes.TargetRuntimeDetails
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationException
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.URI

/**
 * Validates and optionally fixes the computed target runtimes for projects
 * in the Gradle build.
 *
 * Each project may require a set of "target runtimes" that it must be able to run on.
 * Each target runtime reflects a runtime environment where code is executed. For one
 * project to be able to run in a given runtime, all of its dependencies must also
 * support running in that runtime.
 *
 * Some projects have a strict set of runtimes they must support. For example, a
 * plugin that only runs in the daemon would declare the daemon as a target runtime,
 * while a worker action that executes in a worker must declare that it executes in
 * a worker.
 *
 * However, a shared library used by both daemon and worker actions does not necessarily
 * care which runtimes it must execute in. The target runtimes it must support is a function
 * of the daemon and worker project that depends on it. Since both a daemon and worker
 * project depend on this library, that library must also be able to run in the
 * daemon and the worker.
 *
 * In this scenario, both the daemon and the worker project declare required target
 * runtimes. All projects that these two projects depend on will inherit these runtimes
 * as part of their computed target runtimes.
 *
 * This task verifies that the computed target runtimes for each project properly reflect
 * the required target runtimes of all other projects. Optionally, it can write the correct
 * target runtimes back to the build files.
 *
 * @see [gradlebuild.identity.extension.GradleModuleExtension.requiredRuntimes]
 * @see [gradlebuild.identity.extension.GradleModuleExtension.computedRuntimes]
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class CheckTargetRuntimes: DefaultTask() {

    /**
     * A list of paths to all projects that should be checked or fixed.
     */
    @get:Input
    abstract val projectPaths: ListProperty<String>

    /**
     * A list of the same size as [projectPaths], where the nth element contains
     * the target runtime details file for the project with the nth project path.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val targetRuntimeDetailsFiles: ListProperty<File>

    /**
     * A list of the same size as [projectPaths], where the nth element contains
     * the build file for the project with the nth project path.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectBuildFiles: ListProperty<File>

    /**
     * If enabled, automatically fixes invalid computed runtime declarations.
     */
    @get:Input
    @get:Optional
    @get:Option(option = "fix", description = "When enabled, will write the correct computed runtimes back to the build files")
    abstract val fix: Property<Boolean>

    /**
     * If enabled, prints extra details describing why a target runtime declaration is invalid.
     */
    @get:Input
    @get:Optional
    @get:Option(option = "verbose", description = "When enabled, extra details describing why a target runtime declaration is invalid will be printed")
    abstract val verbose: Property<Boolean>

    @TaskAction
    fun execute() {
        val projects = readProjects()
        val shouldFix = fix.getOrElse(false)
        val shouldPrintDetails = verbose.getOrElse(false)
        val computedTargetRuntimes = computeTargetRuntimes(projects)

        requireNoOrphanProjects(projects, computedTargetRuntimes)

        val failures = mutableListOf<String>()
        val mutations = mutableListOf<Pair<File, String>>()
        projects.forEach { (path, info) ->
            val declaredComputed = info.details.computedRuntimes
            val actualComputed = computedTargetRuntimes[path] ?: mapOf()

            if (declaredComputed.toSet() != actualComputed.keys) {
                if (shouldFix) {
                    when (val result = calculateBuildFileMutation(path, actualComputed.keys, info.buildFile)) {
                        is BuildFileMutation.Success -> mutations.add(info.buildFile to result.buildFileText)
                        is BuildFileMutation.Failure -> failures.add(result.message)
                    }
                } else {
                    val extraDetails = if (shouldPrintDetails) {
                        computeExtraDetails(declaredComputed, actualComputed)
                    } else {
                        setOf()
                    }
                    val lines = listOf("Invalid target runtimes for $path. Expected: ${actualComputed.keys} Declared: $declaredComputed") + extraDetails
                    failures.add(lines.joinToString("\n"))
                }
            }
        }

        if (shouldFix) {
            // Only write the mutations if there were no failures
            if (failures.isNotEmpty()) {
                val lines = listOf("""
                    Failed to automatically fix project build files.
                    Make sure each project has a gradleModule.computedRuntimes block directly below the dependencies block:

                    ```
                    dependencies {
                        // ...
                    }

                    gradleModule {
                        computedRuntimes {
                        }
                    }
                    ```
                """.trimIndent(), "", "The following project files could not be automatically updated:") + failures.map { " - $it"}
                throw VerificationException(lines.joinToString("\n"))
            } else {
                mutations.forEach { (file, text) -> file.writeText(text) }
            }
        } else {
            if (failures.isNotEmpty()) {
                var message = """
                    Invalid declared target runtimes found.
                    To automatically fix them, run `./gradlew :checkTargetRuntimes --fix`.
                """.trimIndent()
                if (!shouldPrintDetails) {
                    message += "\nFor more details, run `./gradlew :checkTargetRuntimes --verbose`."
                }
                val lines = listOf(message) + failures.map { " - $it"}
                throw VerificationException(lines.joinToString("\n"))
            }
        }
    }

    private fun readProjects(): Map<String, ProjectInfo> {
        val projects: MutableMap<String, ProjectInfo> = mutableMapOf()
        Gson().let { gson ->
            val paths = projectPaths.get()
            val detailsFiles = targetRuntimeDetailsFiles.get()
            val buildFiles = projectBuildFiles.get()
            require(paths.size == detailsFiles.size)
            require(paths.size == buildFiles.size)

            for (i in paths.indices) {
                val path = paths[i]
                val detailsFile = detailsFiles[i]
                val buildFile = buildFiles[i]

                val details = detailsFile.bufferedReader().use {
                    gson.fromJson(it, TargetRuntimeDetails::class.java)
                }
                projects[path] = ProjectInfo(details, buildFile)
            }
        }

        return projects
    }

    /**
     * Computes the complete set of target runtimes for each project.
     *
     * A project's computed target runtimes are the union of the required runtimes declared by
     * every project that transitively depends on that project. This ensures that if a project
     * which must be able to run on a certain set of runtimes depends on another project,
     * that other project must support the runtimes of the project with requirements.
     *
     * @return A mapping of project paths to their computed runtimes, including
     * the specific projects that triggered each requirement.
     */
    private fun computeTargetRuntimes(projects: Map<String, ProjectInfo>): Map<String, Map<TargetRuntime, Set<String>>> {
        val computedTargetRuntimes = mutableMapOf<String, MutableMap<TargetRuntime, MutableSet<String>>>()
        projects.forEach { (path, info) ->
            val details = info.details
            if (!details.requiredRuntimes.isEmpty()) {
                (details.dependencies + path).forEach { dependency ->
                    details.requiredRuntimes.forEach { requiredRuntime ->
                        computedTargetRuntimes.getOrPut(dependency) {
                            mutableMapOf()
                        }.getOrPut(requiredRuntime) {
                            mutableSetOf()
                        }.add(path)
                    }
                }
            }
        }
        return computedTargetRuntimes
    }

    private fun requireNoOrphanProjects(
        projects: Map<String, ProjectInfo>,
        computedTargetRuntimes: Map<String, Map<TargetRuntime, Set<String>>>
    ) {
        val failures = projects
            .filter { (path, _) -> !computedTargetRuntimes.containsKey(path) }
            .filter { (path, _) -> projects.none { (_, value) -> value.details.dependencies.contains(path) } }
            .map { (path, info) -> "Project $path: ${info.buildFile.asClickableFileUrl()}" }

        if (failures.isNotEmpty()) {
            throw VerificationException(
                """
                    Some projects have not declared required runtimes and are not a dependency of any other projects.
                    Modify the listed buildscripts to include the following `gradleModule` block and fill-in the `requiredRuntimes` section.
                    Then, run `:checkTargetRuntimes --fix`.

                    ```
                    dependencies {
                        // ...
                    }

                    gradleModule {
                        requiredRuntimes {
                            // Fill in this block as required
                        }
                        computedRuntimes {
                        }
                    }
                    ```

                    The following projects must be updated:
                """.trimIndent() + "\n" + failures.joinToString("\n") { " - $it" })
        }
    }

    /**
     * Calculates the new build file text for a project or returns a failure
     * message if the build file could not be updated.
     */
    private fun calculateBuildFileMutation(
        projectPath: String,
        computed: Set<TargetRuntime>,
        buildFile: File
    ): BuildFileMutation {
        val lines = buildFile.readText().lines()

        val start = lines.indexOfFirst {
            Regex("\\s+computedRuntimes\\s+\\{").matches(it)
        }
        if (start == -1) {
            return BuildFileMutation.Failure("Missing computedRuntimes block for $projectPath: ${buildFile.asClickableFileUrl()}")
        }

        val end = lines.indexOfFirst(start + 1) {
            Regex("\\s+}").matches(it)
        }
        if (end == -1) {
            return BuildFileMutation.Failure("Malformed computedRuntimes block for $projectPath: ${buildFile.asClickableFileUrl()}")
        }

        val newTargetRuntimes = computed.map {
            when (it) {
                TargetRuntime.CLIENT -> "client = true"
                TargetRuntime.DAEMON -> "daemon = true"
                TargetRuntime.WORKER -> "worker = true"
            }
        }.sorted()

        val blockContents = listOf("// Auto-generated by `:checkTargetRuntimes --fix`") + newTargetRuntimes

        val newLines = lines.subList(0, start + 1) +
            blockContents.map { " ".repeat(8) + it } +
            lines.subList(end, lines.size)

        return BuildFileMutation.Success(newLines.joinToString("\n"))
    }

    private fun computeExtraDetails(
        declared: Set<TargetRuntime>,
        computed: Map<TargetRuntime, Set<String>>
    ): List<String> {
        return (declared.toSet() + computed.keys).mapNotNull {
            if (declared.contains(it) && computed.containsKey(it)) {
                null
            } else if (declared.contains(it)) {
                "Unnecessary declaration $it"
            } else {
                "Missing declaration $it via ${computed[it]}"
            }
        }.map { "   - $it" }
    }
}


private
data class ProjectInfo(val details: TargetRuntimeDetails, val buildFile: File)


private
sealed interface BuildFileMutation {
    data class Success(val buildFileText: String) : BuildFileMutation
    data class Failure(val message: String) : BuildFileMutation
}


private
fun <T> List<T>.indexOfFirst(startIndex: Int, predicate: (T) -> Boolean): Int {
    require(startIndex in indices) {
        "startIndex must be in range [0, $size): $startIndex"
    }
    return this.subList(startIndex, size).indexOfFirst(predicate).let {
        if (it == -1) -1 else it + startIndex
    }
}


private
fun File.asClickableFileUrl(): String {
    return URI("file", "", toURI().getPath(), null, null).toASCIIString()
}
