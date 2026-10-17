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

package gradlebuild.packageinfo.support

import gradlebuild.traverseGraph
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.Category
import org.gradle.api.provider.Provider

/**
 * The paths of the projects whose modules a distribution's resolved runtime graph carries.
 *
 * The receiver is expected to depend directly on distribution projects only. Every project component reachable
 * from there is a module of the distribution, except for what structurally cannot carry code of its own:
 *
 *  - the distributions themselves, i.e. the direct dependencies of the configuration; the only jar a distribution
 *    contributes is generated glue (the Kotlin DSL extensions) without sources of its own,
 *  - components resolved to a platform variant, which by Gradle's definition carry no code,
 *  - projects of other builds; those cannot apply this build's plugins.
 *
 * Only the dependency graph is inspected, so the returned provider carries no task dependencies.
 */
fun Provider<out Configuration>.distributionModuleProjects(): Provider<Set<String>> = flatMap {
    it.incoming.resolutionResult.run {
        rootComponent.zip(rootVariant, ::collectModuleProjects)
    }
}

private fun collectModuleProjects(root: ResolvedComponentResult, rootVariant: ResolvedVariantResult): Set<String> {
    val distributions: Set<ComponentIdentifier> = root.getDependenciesForVariant(rootVariant)
        .filterIsInstance<ResolvedDependencyResult>()
        .filter { !it.isConstraint }
        .map { it.selected.id }
        .toSet()

    val projects = sortedSetOf<String>()
    traverseGraph(root, rootVariant) { variant ->
        val id = variant.owner
        if (id is ProjectComponentIdentifier && id.isInCurrentBuild() && id !in distributions && !variant.isPlatform()) {
            projects.add(id.projectPath)
        }
    }
    return projects
}

private fun ProjectComponentIdentifier.isInCurrentBuild() = build.buildPath == ":"

private fun ResolvedVariantResult.isPlatform(): Boolean {
    // The resolution result only carries desugared attributes: typed values come back as strings under the same name.
    val categoryAttribute = attributes.keySet().firstOrNull { it.name == Category.CATEGORY_ATTRIBUTE.name } ?: return false
    val category = attributes.getAttribute(categoryAttribute).toString()
    return category == Category.REGULAR_PLATFORM || category == Category.ENFORCED_PLATFORM
}
