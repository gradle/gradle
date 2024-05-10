/*
 * Copyright 2024 the original author or authors.
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

package gradlebuild.process

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.util.ArrayDeque


@CacheableTask
abstract class ValidateTargetProcessCompatibility : DefaultTask() {

    @get:Input
    abstract val ownTargetProcess: Property<String>

    @get:Input
    abstract val runtimeGraphRoot: Property<ResolvedComponentResult>

    @TaskAction
    fun validate() {
        val visited = mutableSetOf<ResolvedComponentResult>()
        val remaining = ArrayDeque<ResolvedComponentResult>()
        remaining.add(runtimeGraphRoot.get())

        while (remaining.isNotEmpty()) {
            val current = remaining.removeFirst()
            visited.add(current)

            // Validate current
            current.variants.forEach {
                it.attributes.getAttribute(TargetProcess.TARGET_PROCESS_ATTRIBUTE)?.let { targetProcess ->
                    validateCompatibility(current.id, targetProcess)
                }
            }

            // Traverse dependencies
            remaining.addAll(
                current.dependencies.filterIsInstance<ResolvedDependencyResult>()
                    .map { it.selected }
                    .filter { it !in visited }
            )
        }
    }

    private
    fun validateCompatibility(other: ComponentIdentifier, otherTarget: String) {
        val platformCompatibility: Map<String, Set<String>> = mapOf(
            "startup" to setOf(),
            "worker" to setOf("startup"),
            "tooling-api" to setOf("startup", "worker"),
            "launcher" to setOf("startup", "worker"),
            "daemon" to setOf("startup", "worker", "tooling-api", "launcher")
        )

        val compatibleProcesses = (platformCompatibility[ownTargetProcess.get()] ?: error("Unknown target process '$otherTarget'.")) + setOf(ownTargetProcess.get())
        if (otherTarget !in compatibleProcesses) {
            throw IllegalStateException(
                "This project has target process '${ownTargetProcess.get()}' and is only compatible with $compatibleProcesses. " +
                    "It depends on component '$other' with incompatible target process '$otherTarget'."
            )
        }
    }
}
