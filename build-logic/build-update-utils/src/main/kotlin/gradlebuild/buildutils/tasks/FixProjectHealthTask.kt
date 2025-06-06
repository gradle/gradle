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

package gradlebuild.buildutils.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault
abstract class FixProjectHealthTask : DefaultTask() {

    private val errorLogFile = File("gradle-project-health.log")

    @TaskAction
    fun fixProjectHealth() {
        if (!errorLogFile.exists()) {
            println("Error log not found! Run the projectHealth check first and redirect output.")
            return
        }

        val dependencyFixes = mutableMapOf<String, MutableList<String>>()
        val dependenciesToRemove = mutableMapOf<String, MutableList<String>>()
        val dependenciesToModify = mutableMapOf<String, MutableList<String>>()

        parseErrorLog(dependencyFixes, dependenciesToRemove, dependenciesToModify)

        modifyDependencies(dependenciesToModify)
        addDependencies(dependencyFixes)
        removeDependencies(dependenciesToRemove)

        println("Project health issues fixed. Run `gradle build` to verify.")
    }

    private fun parseErrorLog(
        dependencyFixes: MutableMap<String, MutableList<String>>,
        dependenciesToRemove: MutableMap<String, MutableList<String>>,
        dependenciesToModify: MutableMap<String, MutableList<String>>
    ) {
        var currentFilePath: String? = null

        errorLogFile.useLines { lines ->
            lines.withIndex().forEach { (index, line) ->
                val fileMatch = Regex("""> (.+?)/build.gradle.kts""").find(line)
                if (fileMatch != null) {
                    currentFilePath = fileMatch.groupValues[1] + "/build.gradle.kts"
                }

                currentFilePath?.let { filePath ->
                    when {
                        line.contains("These transitive dependencies should be declared directly:") -> {
                            dependencyFixes.addToMap(filePath, extractDependencies(index))
                        }

                        line.contains("Existing dependencies which should be modified to be as indicated:") -> {
                            dependenciesToModify.addToMap(filePath, extractDependencies(index))
                        }

                        line.contains("Unused dependencies which should be removed:") -> {
                            dependenciesToRemove.addToMap(filePath, extractDependencies(index))
                        }
                    }
                }
            }
        }
    }

    private fun modifyDependencies(dependenciesToModify: Map<String, List<String>>) {
        dependenciesToModify.forEach { (filePath, dependencies) ->
            val file = File(filePath)
            if (!file.exists()) return@forEach

            val lines = file.readLines().toMutableList()
            dependencies.forEach { dependency ->
                val dependencyRegex = Regex("""(api|implementation)(\(projects\.[a-zA-Z]+\)) \(was (api|implementation)\)""")
                val match = dependencyRegex.find(dependency)
                if (match != null) {
                    val oldDependency = match.groupValues[3] + match.groupValues[2]
                    val newDependency = match.groupValues[1] + match.groupValues[2]
                    lines.replaceAll { it.replace(oldDependency, newDependency) }
                }
            }
            file.writeText(lines.joinToString("\n"))
        }
    }

    private fun addDependencies(dependencyFixes: Map<String, List<String>>) {
        dependencyFixes.forEach { (filePath, dependencies) ->
            val file = File(filePath)
            if (!file.exists()) return@forEach

            val lines = file.readLines().toMutableList()
            val dependenciesIndex = lines.indexOfFirst { it.contains("dependencies {") }

            val whitespace = lines.drop(dependenciesIndex + 1).find { it.isNotBlank() }?.takeWhile { it.isWhitespace() } ?: ""
            lines.addAll(dependenciesIndex + 1, dependencies.map { "$whitespace$it" } + listOf(""))
            file.writeText(lines.joinToString("\n"))
        }
    }

    private fun removeDependencies(dependenciesToRemove: Map<String, List<String>>) {
        dependenciesToRemove.forEach { (filePath, dependencies) ->
            val file = File(filePath)
            if (!file.exists()) return@forEach

            val lines = file.readLines().filter { line -> dependencies.none { line.contains(it) } }
            file.writeText(lines.joinToString("\n"))
        }
    }

    private fun extractDependencies(startLine: Int): List<String> {
        val dependencies = mutableListOf<String>()
        errorLogFile.useLines { lines ->
            lines.drop(startLine).forEach { line ->
                if (line.isBlank() || line.startsWith(">")) return@useLines
                val regex = Regex("""\s*(api|implementation)\(([^)]+)\)\s*""")
                regex.find(line)?.let {
                    dependencies.add(replaceProjectDependency(line.trim()))
                }
            }
        }
        return dependencies
    }

    private fun replaceProjectDependency(line: String): String {
        val projectDependencyRegex = Regex("""project\(["']([^"']+)["']\)""")
        return line.replace(projectDependencyRegex) { match ->
            val projectName = match.groupValues[1]
            val parts = projectName.split(":").last().split("-")
            "projects.${parts.first()}${parts.drop(1).joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }}"
        }
    }

    private fun MutableMap<String, MutableList<String>>.addToMap(filePath: String, values: List<String>) {
        this.computeIfAbsent(filePath) { mutableListOf() }.addAll(values)
    }
}
