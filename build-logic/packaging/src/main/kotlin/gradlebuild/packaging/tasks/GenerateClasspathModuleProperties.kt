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

package gradlebuild.packaging.tasks

import gradlebuild.basics.util.ReproduciblePropertiesWriter
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.Properties

/**
 * Given a configuration, produces a .properties for each artifact in the configuration.
 * The properties file describes the artifact's dependencies, so that its runtime classpath
 * can be analyzed and reconstructed later.
 *
 * This task assumes each component in the graph has a single variant and each variant
 * has a single file.
 */
@DisableCachingByDefault(because = "Unable to snapshot ComponentIdentifier")
abstract class GenerateClasspathModuleProperties : DefaultTask() {

    @get:Internal
    abstract val artifacts: ListProperty<ModuleArtifact>

    @get:Internal
    abstract val moduleNodes: MapProperty<ComponentIdentifier, ModuleNode>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    fun configureFrom(configuration: Configuration) {
        this.artifacts.set(
            configuration.incoming.artifacts.resolvedArtifacts.map {
                it.map { artifact ->
                    ModuleArtifact(
                        artifact.id.componentIdentifier,
                        artifact.file.name
                    )
                }
            }
        )

        this.moduleNodes.set(
            configuration.incoming.resolutionResult.run {
                rootComponent.zip(rootVariant, this@GenerateClasspathModuleProperties::traverseGraph)
            }
        )
    }

    fun traverseGraph(
        rootComponent: ResolvedComponentResult,
        rootVariant: ResolvedVariantResult
    ): Map<ComponentIdentifier, ModuleNode> {
        val result = mutableMapOf<ComponentIdentifier, ModuleNode>()

        val seen = mutableSetOf<ResolvedVariantResult>()
        val queue = ArrayDeque<Pair<ResolvedComponentResult, ResolvedVariantResult>>()

        seen.add(rootVariant)
        queue.add(rootComponent to rootVariant)

        while (queue.isNotEmpty()) {
            val (component, variant) = queue.removeFirst()
            val dependencies = component.getDependenciesForVariant(variant)

            val dependencyComponents = mutableSetOf<ComponentIdentifier>()
            for (dependency in dependencies) {
                if (dependency.isConstraint) {
                    continue
                }
                val resolvedDependency = when (dependency) {
                    is ResolvedDependencyResult -> dependency
                    is UnresolvedDependencyResult -> throw dependency.failure
                    else -> throw AssertionError("Unknown dependency type: $result")
                }

                val targetVariant = resolvedDependency.resolvedVariant
                val targetComponent = resolvedDependency.selected

                dependencyComponents.add(targetComponent.id)
                if (seen.add(targetVariant)) {
                    queue.add(targetComponent to targetVariant)
                }
            }

            // The root variant is synthetic and does not contribute a
            // module to the distribution
            if (variant != rootVariant) {
                val (moduleName, alias) = getIdentity(component)
                val existing = result.put(
                    component.id,
                    ModuleNode(
                        moduleName,
                        alias,
                        dependencyComponents
                    )
                )
                require(existing == null) {
                    "Cannot have multiple variants for component $component"
                }
            }
        }

        return result
    }

    private fun getIdentity(component: ResolvedComponentResult): Pair<String, ModuleAlias?> {
        when (val id = component.id) {
            is ModuleComponentIdentifier -> {
                val moduleName: String = id.module
                val alias = ModuleAlias(id.group, id.module, id.version)
                return moduleName to alias
            }
            is ProjectComponentIdentifier -> {
                val moduleName: String = "gradle-" + id.projectName
                val alias: ModuleAlias? = component.moduleVersion?.let {
                    ModuleAlias(it.group, it.name, it.version)
                }
                return moduleName to alias
            }
            else -> throw AssertionError("Unknown component type ${component.id}")
        }
    }

    data class ModuleNode(
        val name: String,
        val alias: ModuleAlias?,
        val dependencyComponentIds: Set<ComponentIdentifier>
    )

    data class ModuleArtifact(
        val id: ComponentIdentifier,
        val artifactFileName: String
    )

    @TaskAction
    fun generate() {
        val outputDirectory = outputDir.get()
        val nodesByComponentId = moduleNodes.get()

        val modulesByName: Map<String, Pair<ModuleNode, ModuleArtifact>> = artifacts.get().associate { artifact ->
            val node = nodesByComponentId[artifact.id]
            require(node != null) {
                "Could not find node for artifact ${artifact.id}"
            }
            node.name to Pair(node, artifact)
        }

        modulesByName.values.forEach { pair ->
            val (node, artifact) = pair
            val dependencyNames = node.dependencyComponentIds.mapNotNull {
                val dependencyNode = nodesByComponentId[it]
                require(dependencyNode != null) {
                    "Dependency '$it' of module '${artifact.id}' could not be found"
                }
                val targetModuleName = dependencyNode.name
                if (modulesByName.containsKey(targetModuleName)) {
                    targetModuleName
                } else {
                    // We may not have resolved an artifact for a given node,
                    // so we may not have a module. This can happen when the
                    // dependency is a platform, and does not contribute artifacts.
                    null
                }
            }

            generateProperties(
                dependencyNames,
                node.alias,
                artifact.artifactFileName,
                outputDirectory.file(node.name + ".properties").asFile
            )
        }
    }

}


data class ModuleAlias(
    val group: String,
    val name: String,
    val version: String
)


@CacheableTask
abstract class GenerateEmptyModuleProperties : DefaultTask() {

    @get:Input
    abstract val artifactFileName: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        generateProperties(
            listOf(),
            null,
            artifactFileName.get(),
            outputFile.get().asFile
        )
    }

}


fun generateProperties(
    dependencies: List<String>,
    moduleAlias: ModuleAlias?,
    artifactFileName: String,
    propertiesFile: File
) {
    val properties = Properties().also { properties ->
        properties["dependencies"] = dependencies.sorted().joinToString(",")
        properties["jarFile"] = artifactFileName
        moduleAlias?.run {
            properties["alias.group"] = group
            properties["alias.name"] = name
            properties["alias.version"] = version
        }
    }
    ReproduciblePropertiesWriter.store(properties, propertiesFile)
}
