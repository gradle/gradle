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
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.Properties

/**
 * Given a configuration, produces a .properties for each node in the configuration's resolved
 * graph. The properties file describes the node's dependencies, and stores its associated artifact,
 * so that classpaths derived from the graph can be analyzed and reconstructed later.
 *
 * This task assumes each component in the graph has a single variant and each variant
 * has at most one artifact.
 */
@DisableCachingByDefault(because = "Unable to snapshot ComponentIdentifier") // Can be made cacheable after https://github.com/gradle/gradle/pull/36174
abstract class GenerateClasspathModuleProperties : DefaultTask() {

    @get:Input
    abstract val artifactNames: ListProperty<String>

    @get:Internal // Can be declared input after https://github.com/gradle/gradle/pull/36174
    abstract val artifactComponentIds: ListProperty<ComponentIdentifier>

    @get:Nested
    abstract val graphNodes: MapProperty<ComponentIdentifier, GraphNode>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    fun configureFrom(configuration: Configuration) {
        val artifacts = configuration.incoming.artifacts.resolvedArtifacts

        this.artifactNames.set(
            artifacts.map {
                it.map { artifact -> artifact.file.name }
            }
        )
        this.artifactComponentIds.set(
            artifacts.map {
                it.map { artifact -> artifact.id.componentIdentifier }
            }
        )

        this.graphNodes.set(
            configuration.incoming.resolutionResult.run {
                rootComponent.zip(rootVariant, this@GenerateClasspathModuleProperties::traverseGraph)
            }
        )
    }

    fun traverseGraph(
        rootComponent: ResolvedComponentResult,
        rootVariant: ResolvedVariantResult
    ): Map<ComponentIdentifier, GraphNode> {
        val result = mutableMapOf<ComponentIdentifier, GraphNode>()

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
                    GraphNode(
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
                return moduleName to null
            }
            else -> throw AssertionError("Unknown component type ${component.id}")
        }
    }

    data class GraphNode(
        @get:Input val moduleName: String,
        @get:Nested @get:Optional val alias: ModuleAlias?,
        @get:Internal val dependencyComponentIds: Set<ComponentIdentifier> // Can be declared input after https://github.com/gradle/gradle/pull/36174
    )

    @TaskAction
    fun generate() {
        val outputDirectory = outputDir.get()
        val nodesByComponentId = graphNodes.get()

        val nodesWithoutArtifacts = nodesByComponentId.toMutableMap()

        // Generate a module for each node with an artifact
        val names = artifactNames.get()
        val ids = artifactComponentIds.get()
        require(names.size == ids.size)
        for (i in names.indices) {
            val artifactFileName = names[i]
            val componentId = ids[i]

            nodesWithoutArtifacts.remove(componentId)
            val graphNode = nodesByComponentId[componentId] ?:
                error("Could not find graph node for artifact $componentId")

            val dependencyNames = graphNode.dependencyComponentIds.map {
                nodesByComponentId.getModuleName(it)
            }

            generateProperties(
                dependencyNames,
                graphNode.alias,
                artifactFileName,
                outputDirectory.file(graphNode.moduleName + ".properties").asFile
            )
        }

        // Generate a module for remaining nodes without artifacts
        // These can be platforms/boms, or multi-component/KMP nodes like kotlinx
        nodesWithoutArtifacts.values.forEach { node ->
            val dependencyNames = node.dependencyComponentIds.map {
                nodesByComponentId.getModuleName(it)
            }

            generateProperties(
                dependencyNames,
                node.alias,
                null,
                outputDirectory.file(node.moduleName + ".properties").asFile
            )
        }
    }

    private
    fun Map<ComponentIdentifier, GraphNode>.getModuleName(identifier: ComponentIdentifier): String =
        get(identifier)?.moduleName ?: error("Dependency '$identifier' could not be found")

}


data class ModuleAlias(
    @get:Input val group: String,
    @get:Input val name: String,
    @get:Input val version: String
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
    artifactFileName: String?,
    propertiesFile: File
) {
    val properties = Properties().also { properties ->
        properties["dependencies"] = dependencies.sorted().joinToString(",")
        artifactFileName?.let {
            properties["jarFile"] = it
        }
        moduleAlias?.run {
            properties["alias.group"] = group
            properties["alias.name"] = name
            properties["alias.version"] = version
        }
    }
    ReproduciblePropertiesWriter.store(properties, propertiesFile)
}
