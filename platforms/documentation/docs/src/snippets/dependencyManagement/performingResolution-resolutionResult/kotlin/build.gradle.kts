plugins {
    id("jvm-ecosystem")
}

// tag::declare-configurations[]
val implementation = configurations.dependencyScope("implementation")
val runtimeClasspath = configurations.resolvable("runtimeClasspath") {
    extendsFrom(implementation.get())
}
// end::declare-configurations[]

repositories {
    mavenCentral()
}

// tag::declaring-dependencies[]
dependencies {
    implementation("com.google.guava:guava:33.2.1-jre")
}
// end::declaring-dependencies[]

// tag::define-graph-traversal-task[]
abstract class GenerateDot : DefaultTask() {

    @get:Input
    abstract val rootComponent: Property<ResolvedComponentResult>

    @get:Input
    abstract val rootVariant: Property<ResolvedVariantResult>

    @TaskAction
    fun traverse() {
        println("digraph {")
        traverseGraph(
            rootComponent.get(),
            rootVariant.get(),
            { node -> println("    ${toNodeId(node)} [shape=box]") },
            { from, to -> println("    ${toNodeId(from)} -> ${toNodeId(to)}") }
        )
        println("}")
    }

    fun toNodeId(variant: ResolvedVariantResult): String {
        return "\"${variant.owner.displayName}:${variant.displayName}\""
    }
    // end::define-graph-traversal-task[]

    // tag::graph-traversal-function[]
    fun traverseGraph(
        rootComponent: ResolvedComponentResult,
        rootVariant: ResolvedVariantResult,
        nodeCallback: (ResolvedVariantResult) -> Unit,
        edgeCallback: (ResolvedVariantResult, ResolvedVariantResult) -> Unit
    ) {
        val seen = mutableSetOf<ResolvedVariantResult>(rootVariant)
        nodeCallback(rootVariant)

        val queue = ArrayDeque(listOf(rootVariant to rootComponent))
        while (queue.isNotEmpty()) {
            val (variant, component) = queue.removeFirst()

            // Traverse this variant's dependencies
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

                    edgeCallback(variant, toVariant)
                }
            }
        }
    }
    // end::graph-traversal-function[]

    // tag::define-graph-traversal-task[]
}
// end::define-graph-traversal-task[]

// tag::register-graph-traversal-task[]
tasks.register<GenerateDot>("generateDot") {
    rootComponent = runtimeClasspath.flatMap {
        it.incoming.resolutionResult.rootComponent
    }
    rootVariant = runtimeClasspath.flatMap {
        it.incoming.resolutionResult.rootVariant
    }
}
// end::register-graph-traversal-task[]
