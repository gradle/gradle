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

package gradlebuild.nullaway

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

internal abstract class NullawayStatusService : BuildService<BuildServiceParameters.None>, Closeable {
    private val projectsWithNullAwayEnabled = ConcurrentHashMap.newKeySet<String>()
    private val projectsWithUncheckedDeps = ConcurrentHashMap.newKeySet<String>()
    private val projectsToEnableNullaway = ConcurrentHashMap.newKeySet<String>()

    private fun ensureProjectNotSeen(projectPath: String) {
        require(projectPath !in projectsWithNullAwayEnabled)
        require(projectPath !in projectsWithUncheckedDeps)
        require(projectPath !in projectsToEnableNullaway)
    }

    fun addProjectWithNullawayEnabled(projectPath: String) {
        ensureProjectNotSeen(projectPath)

        projectsWithNullAwayEnabled.add(projectPath)
    }

    fun addProjectWithUncheckedDeps(projectPath: String) {
        ensureProjectNotSeen(projectPath)

        projectsWithUncheckedDeps.add(projectPath)
    }

    fun addProjectToEnableNullawayIn(projectPath: String) {
        ensureProjectNotSeen(projectPath)

        projectsToEnableNullaway.add(projectPath)
    }

    private val seenProjectsCount: Int
        get() = projectsWithNullAwayEnabled.size + projectsWithUncheckedDeps.size + projectsToEnableNullaway.size

    override fun close() {
        require(seenProjectsCount > 0) {
            "NullawayStatusService was created but no status tasks have run"
        }

        printResult {
            println("Collected status of ${seenProjectsCount} ${projectS(seenProjectsCount)}.")

            val enabledCount = projectsWithNullAwayEnabled.size
            val toEnableCount = projectsToEnableNullaway.size
            val uncheckedCount = projectsWithUncheckedDeps.size

            if (enabledCount == seenProjectsCount) {
                println("All have NullAway enabled.")
            } else {
                println("NullAway enabled in ${enabledCount} ${projectS(enabledCount)}.")

                if (projectsToEnableNullaway.isNotEmpty()) {
                    println("${toEnableCount} ${projectS(toEnableCount)} with checked dependencies are ready to be worked on:")
                    projectsToEnableNullaway.sorted().forEach {
                        println("  * $it")
                    }
                } else if (projectsWithUncheckedDeps.isNotEmpty()) {
                    println("Unchecked dependencies of $uncheckedCount ${projectS(uncheckedCount)} need NullAway enabled first (run `./gradlew nullawayStatus` to see those).")
                } else {
                    // This should not happen.
                    error("Invalid projects count: $seenProjectsCount != $enabledCount + $toEnableCount + $uncheckedCount")
                }
            }
        }
    }

    private fun projectS(num: Int) = if (num == 1) "project" else "projects"

    private inline fun printResult(block: NullawayStatusService.() -> Unit) {
        val delimiter = "=".repeat(72)
        println(delimiter)
        block()
        println(delimiter)
    }

    companion object {
        val SERVICE_NAME = NullawayStatusService::class.qualifiedName!!
    }
}
