import com.google.gson.Gson
import gradlebuild.identity.extension.GradleModuleExtension
import gradlebuild.identity.extension.ModuleTargetRuntimes
import gradlebuild.runtimes.TargetRuntimeDetails
import gradlebuild.runtimes.TargetRuntimesAttribute

/**
 * Plugin implementing target runtime support for individual Gradle modules.
 *
 * This plugin enforces the [ModuleTargetRuntimes] for each Gradle module are consistent, in that
 * all dependencies of a module must have at least the same target runtimes as the module itself.
 *
 * Additionally, this plugin exposes a variant containing the target runtimes and dependencies of the module.
 * This file is consumed by the root project in order to automate configuration of the target runtime flags.
 */

plugins {
    id("java-library")
    id("gradlebuild.module-identity")
}

val gradleModule = the<GradleModuleExtension>()
enforceCrossProjectCompatibility(gradleModule)
advertiseTargetRuntimeDetails(gradleModule)

class TargetRuntimesAttributeCompatibilityRule : AttributeCompatibilityRule<String> {
    override fun execute(details: CompatibilityCheckDetails<String>) {
        if (details.consumerValue == null || details.producerValue == null) {
            // Either the consumer doesn't care about target platforms or the producer
            // does not define any specific platform support.
            details.compatible()
            return
        }

        val producerPlatforms = TargetRuntimesAttribute.decode(details.producerValue!!)
        val consumerPlatforms = TargetRuntimesAttribute.decode(details.consumerValue!!)

        if (producerPlatforms.containsAll(consumerPlatforms)) {
            // The producer supports all the platforms the consumer requires.
            details.compatible()
        } else {
            // The producer does not support all the platforms the consumer requires.
            details.incompatible()
        }
    }
}

/**
 * Requires that all projects that this project depends on is compatible
 * with the same target runtimes as this project.
 */
fun enforceCrossProjectCompatibility(gradleModule: GradleModuleExtension) {
    val targetRuntimes = gradleModule.targetRuntimes.asList().map(TargetRuntimesAttribute::encode)

    listOf(configurations.apiElements, configurations.runtimeElements).configureEach {
        attributes {
            // Advertise our supported target runtimes on our variants
            attributeProvider(TargetRuntimesAttribute.ATTRIBUTE, targetRuntimes)
        }
    }

//    listOf(configurations.runtimeClasspath, configurations.compileClasspath).configureEach {
//        attributes {
//            // Require that all dependencies are compatible with our target runtimes
//            attributeProvider(TargetRuntimesAttribute.ATTRIBUTE, targetRuntimes)
//        }
//    }

    dependencies {
        attributesSchema {
            attribute(TargetRuntimesAttribute.ATTRIBUTE) {
                compatibilityRules.add(TargetRuntimesAttributeCompatibilityRule::class.java)
            }
        }
    }
}

fun ModuleTargetRuntimes.asList(): Provider<List<String>> {
    val targetRuntimeValues = mapOf(
        usedForStartup to listOf(TargetRuntimesAttribute.STARTUP),
        usedInWrapper to listOf(TargetRuntimesAttribute.WRAPPER),
        usedInWorkers to listOf(TargetRuntimesAttribute.WORKER),
        usedInClient to listOf(TargetRuntimesAttribute.CLIENT),
        usedInDaemon to listOf(TargetRuntimesAttribute.DAEMON)
    )

    val runtimes = reduceBooleanFlagValues(targetRuntimeValues) { a, b -> a + b }
    return runtimes.orElse(emptyList())
}

fun <T: Any> Iterable<NamedDomainObjectProvider<T>>.configureEach(action: T.() -> Unit) {
    forEach { provider ->
        provider.configure {
            action()
        }
    }
}

@DisableCachingByDefault(because = "Not worth caching")
abstract class GenerateTargetRuntimeDetails: DefaultTask() {

    @get:Input
    abstract val entryPoint: Property<Boolean>

    @get:Input
    abstract val targetRuntimes: SetProperty<String>

    @get:Input
    abstract val rootComponent: Property<ResolvedComponentResult>

    @get:Input
    abstract val rootVariant: Property<ResolvedVariantResult>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val components = mutableSetOf<String>()
        traverseGraph(rootComponent.get(), rootVariant.get()) { node ->
            when (val id = node.owner) {
                is ProjectComponentIdentifier -> when {
                    // Only include projects in the root build for now
                    // Also only include projects that declare target runtimes, as some project dependencies
                    // like platforms (:distributions-dependencies) do not contribute artifacts and therefore
                    // are not meant to declare target runtimes.
                    id.projectPath == id.buildTreePath && node.attributes.getAttribute(TargetRuntimesAttribute.ATTRIBUTE) != null -> components.add(id.buildTreePath)
                }
            }
        }
        outputFile.get().asFile.bufferedWriter().use { writer ->
            Gson().toJson(
                TargetRuntimeDetails(
                    entryPoint = entryPoint.get(),
                    targetRuntimes = targetRuntimes.get(),
                    dependencies = components
                ),
                writer
            )
        }
    }

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
}

@Suppress("UnstableApiUsage")
fun advertiseTargetRuntimeDetails(gradleModule: GradleModuleExtension) {
    val detailsTask = tasks.register<GenerateTargetRuntimeDetails>("generateTargetRuntimeDetails") {
        entryPoint = gradleModule.entryPoint
        targetRuntimes = gradleModule.targetRuntimes.asList()
        rootComponent = configurations.runtimeClasspath.flatMap { it.incoming.resolutionResult.rootComponent }
        rootVariant = configurations.runtimeClasspath.flatMap { it.incoming.resolutionResult.rootVariant }
        outputFile = project.layout.buildDirectory.dir("tmp").map { it.file("targetRuntimeDetails.properties") }
    }

    configurations.consumable("targetRuntimeDetails") {
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named("target-runtime-details"))
            attribute(Usage.USAGE_ATTRIBUTE, objects.named("target-runtime-details"))
        }

        outgoing.artifact(detailsTask)
    }
}
