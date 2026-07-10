repositories {
    mavenCentral()
}

val scm = configurations.create("scm")

dependencies {
    scm("org.eclipse.jgit:org.eclipse.jgit:4.9.2.201712150930-r")
    scm("commons-codec:commons-codec:1.7")
    scm("some:unresolved:2.5")
}

// tag::walk-task[]
tasks.register<DependencyGraphWalk>("walkDependencyGraph") {
    rootComponent = configurations["scm"].incoming.resolutionResult.rootComponent
}

abstract class DependencyGraphWalk: DefaultTask() {

    @get:Input
    abstract val rootComponent: Property<ResolvedComponentResult>

    @TaskAction
    fun walk() {
        val root: ResolvedComponentResult = rootComponent.get()
        traverseDependencies(0, root.dependencies)
    }

    private fun traverseDependencies(level: Int, results: Set<DependencyResult>) {
        results.forEach { result ->
            if (result is ResolvedDependencyResult) {
                val componentResult: ResolvedComponentResult = result.selected
                val componentIdentifier: ComponentIdentifier = componentResult.id
                val node: String = "${calculateIndentation(level)}- ${componentIdentifier.displayName} (${componentResult.selectionReason})"
                logger.quiet(node)
                traverseDependencies(level + 1, componentResult.dependencies)
            } else if (result is UnresolvedDependencyResult) {
                val componentSelector: ComponentSelector = result.attempted
                val node: String = "${calculateIndentation(level)}- ${componentSelector.displayName} (failed)"
                logger.quiet(node)
            }
        }
    }

    private fun calculateIndentation(level: Int) = "     ".repeat(level)
}
// end::walk-task[]
