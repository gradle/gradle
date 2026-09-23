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

package gradlebuild

import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

/**
 * Traverses a resolved dependency graph variant by variant, calling [visit] once for every variant reached from
 * [rootVariant] of [rootComponent]. The root variant itself is not visited.
 *
 * Constraints are not followed, and an unresolved dependency fails the traversal with its resolution failure.
 * The component a variant belongs to is available as [ResolvedVariantResult.getOwner].
 */
fun traverseGraph(
    rootComponent: ResolvedComponentResult,
    rootVariant: ResolvedVariantResult,
    visit: (ResolvedVariantResult) -> Unit
) {
    val seen = mutableSetOf(rootVariant)
    val queue = ArrayDeque(listOf(rootVariant to rootComponent))
    while (queue.isNotEmpty()) {
        val (variant, component) = queue.removeFirst()
        component.getDependenciesForVariant(variant).forEach { dependency ->
            val resolved = when (dependency) {
                is ResolvedDependencyResult -> dependency
                is UnresolvedDependencyResult -> throw dependency.failure
                else -> throw AssertionError("Unknown dependency type: $dependency")
            }

            if (!resolved.isConstraint) {
                val toVariant = resolved.resolvedVariant

                if (seen.add(toVariant)) {
                    visit(toVariant)
                    queue.addLast(toVariant to resolved.selected)
                }
            }
        }
    }
}
