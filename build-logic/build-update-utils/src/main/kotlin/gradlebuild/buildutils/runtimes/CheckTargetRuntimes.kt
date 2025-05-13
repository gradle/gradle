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
import gradlebuild.runtimes.TargetRuntimeDetails
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationException
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Not worth caching")
abstract class CheckTargetRuntimes: DefaultTask() {

    @get:Nested
    abstract val targetRuntimes: ListProperty<ProjectTargetRuntimeDetails>

    @get:Internal
    abstract val projectBuildFiles: ListProperty<ProjectBuildFileDetails>

    @get:Input
    @get:Optional
    @get:Option(option = "fix", description = "When enabled, will write the correct target runtimes back to the build files")
    abstract val fix: Property<Boolean>

    @TaskAction
    fun execute() {
        val declaredDetails = readTargetRuntimes()
        val computedTargetRuntimes = computeTargetRuntimes(declaredDetails)
        val failures = fixOrCollectFailures(declaredDetails, computedTargetRuntimes)

        if (failures.isNotEmpty()) {
            val message = "Invalid declared target runtimes found. To fix them, run `./gradlew :checkTargetRuntimes --fix`:"
            val lines = listOf(message) + failures.map { " - $it"}
            throw VerificationException(lines.joinToString("\n"))
        }
    }

    private fun readTargetRuntimes(): Map<String, TargetRuntimeDetails> {
        return Gson().let { gson ->
            targetRuntimes.get().associateBy(ProjectTargetRuntimeDetails::projectPath) { proj ->
                proj.detailsFile.bufferedReader().use {
                    gson.fromJson(it, TargetRuntimeDetails::class.java)
                }
            }
        }
    }

    private fun computeTargetRuntimes(declaredDetails: Map<String, TargetRuntimeDetails>): Map<String, Set<String>> {
        val computedTargetRuntimes = mutableMapOf<String, MutableSet<String>>()
        declaredDetails.forEach { entry ->
            val details = entry.value
            if (details.entryPoint) {
                (details.dependencies + entry.key).forEach {
                    computedTargetRuntimes.getOrPut(it) { mutableSetOf() }.addAll(details.targetRuntimes)
                }
            }
        }
        return computedTargetRuntimes
    }

    private fun fixOrCollectFailures(
        declaredDetails: Map<String, TargetRuntimeDetails>,
        computedTargetRuntimes: Map<String, Set<String>>
    ): MutableList<String> {
        val shouldFix = fix.getOrElse(false)
        val buildFilesByPath = projectBuildFiles.get().associateBy({ it.projectPath }, { it.buildFile })

        val failures = mutableListOf<String>()
        (declaredDetails.keys + computedTargetRuntimes.keys).forEach {
            val declared = declaredDetails[it]?.targetRuntimes ?: setOf()
            val computed = computedTargetRuntimes[it] ?: setOf()

            if (declared.toSet() != computed) {
                if (shouldFix) {
                    writeTargetRuntimesTo(buildFilesByPath, it, computed)
                } else {
                    failures.add("Invalid target runtimes for $it. Expected: $computed Declared: $declared")
                }
            }
        }
        return failures
    }

    private fun writeTargetRuntimesTo(buildFilesByPath: Map<String, File>, it: String, computed: Set<String>) {
        val buildFile = buildFilesByPath[it]
        require(buildFile != null) {
            "Build file for $it not found."
        }

        val lines = buildFile.readText().lines()

        val start = lines.indexOfFirst {
            Regex("\\s+targetRuntimes\\s+\\{").matches(it)
        }
        require(start != -1) {
            "targetRuntimes block not found for $it"
        }

        val end = lines.indexOfFirst(start + 1) {
            Regex("\\s+}").matches(it)
        }
        require(end != -1) {
            "targetRuntimes block not closed for $it"
        }

        val newTargetRuntimes = computed.sorted().map {
            " ".repeat(8) + when (it) {
                "STARTUP" -> "usedForStartup = true"
                "WRAPPER" -> "usedInWrapper = true"
                "WORKER" -> "usedInWorkers = true"
                "CLIENT" -> "usedInClient = true"
                "DAEMON" -> "usedInDaemon = true"
                else -> throw RuntimeException("Unknown target runtime: $it")
            }
        }

        val newLines = lines.subList(0, start + 1) +
            newTargetRuntimes +
            lines.subList(end, lines.size)

        buildFile.writeText(newLines.joinToString("\n"))
    }
}

private fun <T> List<T>.indexOfFirst(startIndex: Int, predicate: (T) -> Boolean): Int {
    require(startIndex in indices) {
        "startIndex must be in range [0, $size): $startIndex"
    }
    return this.subList(startIndex, size).indexOfFirst(predicate).let {
        if (it == -1) -1 else it + startIndex
    }
}
