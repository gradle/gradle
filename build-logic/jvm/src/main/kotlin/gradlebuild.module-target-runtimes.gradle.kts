import com.google.gson.Gson
import gradlebuild.identity.extension.GradleModuleExtension
import gradlebuild.identity.extension.ModuleTargetRuntimes
import gradlebuild.runtimes.TargetRuntime
import gradlebuild.runtimes.TargetRuntimeDetails

/**
 * This plugin exposes a variant containing the declared target runtimes and dependencies of this module.
 * This file is consumed by the root project in order to automate configuration of the target runtime flags.
 */

plugins {
    id("java-library")
    id("gradlebuild.module-identity")
}

val gradleModule = the<GradleModuleExtension>()
val detailsTask = tasks.register<GenerateTargetRuntimeDetails>("generateTargetRuntimeDetails") {
    requiredRuntimes = gradleModule.requiredRuntimes.asList()
    computedRuntimes = gradleModule.computedRuntimes.asList()
    dependencyProjectPaths = configurations.runtimeClasspath.collectTransitiveProjectDependencies().zip(
        configurations.compileClasspath.collectTransitiveProjectDependencies()
    ) { a, b -> a + b}
    outputFile = project.layout.buildDirectory.dir("tmp").map { it.file("target-runtime-details.json") }
}

configurations.consumable("targetRuntimeDetails") {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("target-runtime-details"))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("target-runtime-details"))
    }
    outgoing.artifact(detailsTask)
}

/**
 * Writes a JSON file containing target runtime details and project dependencies.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class GenerateTargetRuntimeDetails: DefaultTask() {

    @get:Input
    abstract val requiredRuntimes: SetProperty<TargetRuntime>

    @get:Input
    abstract val computedRuntimes: SetProperty<TargetRuntime>

    @get:Input
    abstract val dependencyProjectPaths: SetProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        outputFile.get().asFile.bufferedWriter().use { writer ->
            Gson().toJson(
                TargetRuntimeDetails(
                    requiredRuntimes = requiredRuntimes.get(),
                    computedRuntimes = computedRuntimes.get(),
                    dependencies = dependencyProjectPaths.get()
                ),
                writer
            )
        }
    }
}

/**
 * Get a provider containing all projects in the current build this configuration transitively depends on.
 */
private
fun NamedDomainObjectProvider<Configuration>.collectTransitiveProjectDependencies(): Provider<Set<String>> {
    return flatMap {
        it.incoming.resolutionResult.run {
            rootComponent.zip(rootVariant, ::collectProjectComponentPaths)
        }
    }
}

/**
 * Collects the paths for all projects from the current build in the given dependency graph.
 */
private
fun collectProjectComponentPaths(
    rootComponent: ResolvedComponentResult,
    rootVariant: ResolvedVariantResult
): Set<String> {
    val components = mutableSetOf<String>()
    traverseGraph(rootComponent, rootVariant) { node ->
        when (val id = node.owner) {
            is ProjectComponentIdentifier -> when {
                // Only collect projects in the root build for now
                id.projectPath == id.buildTreePath -> components.add(id.buildTreePath)
            }
        }
    }
    return components
}

/**
 * Traverses the dependency graph variant-by-variant, calling the given
 * callback for each node.
 */
private
fun traverseGraph(
    rootComponent: ResolvedComponentResult,
    rootVariant: ResolvedVariantResult,
    nodeCallback: (ResolvedVariantResult) -> Unit
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
                    nodeCallback(toVariant)
                    queue.addLast(toVariant to resolved.selected)
                }
            }
        }
    }
}


/**
 * Get all declared target runtimes as a list.
 */
private
fun ModuleTargetRuntimes.asList(): Provider<List<TargetRuntime>> {
    val targetRuntimeValues = mapOf(
        client to listOf(TargetRuntime.CLIENT),
        daemon to listOf(TargetRuntime.DAEMON),
        worker to listOf(TargetRuntime.WORKER)
    )

    val runtimes = reduceBooleanFlagValues(targetRuntimeValues) { a, b -> a + b }
    return runtimes.orElse(emptyList())
}
