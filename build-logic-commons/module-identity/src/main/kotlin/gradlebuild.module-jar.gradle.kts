import gradlebuild.basics.tasks.ClasspathManifest
import gradlebuild.identity.extension.ModuleIdentityExtension
import java.util.jar.Attributes

plugins {
    id("gradlebuild.module-identity")
}

val moduleIdentity = the<ModuleIdentityExtension>()

configureJarTasks()

pluginManager.withPlugin("java-base") {
    configureClasspathManifestGeneration()
}

fun configureJarTasks() {
    tasks.withType<Jar>().configureEach {
        archiveBaseName = moduleIdentity.baseName
        archiveVersion = moduleIdentity.version.map { it.baseVersion.version }
        manifest.attributes(
            mapOf(
                Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
                Attributes.Name.IMPLEMENTATION_VERSION.toString() to moduleIdentity.version.map { it.baseVersion.version }
            )
        )
    }
}

fun configureClasspathManifestGeneration() {
    val runtimeClasspath by configurations
    val externalComponents by lazy {
        runtimeClasspath.incoming.resolutionResult.rootComponent.map { rootComponent ->
            val rootVariant = rootComponent.variants.find { it.displayName == runtimeClasspath.name }
            rootVariant?.let { computeExternalDependenciesNotAccessibleFromProjectDependencies(rootComponent, it) } ?: emptySet()
        }.get()
    }
    val classpathManifest = tasks.register("classpathManifest", ClasspathManifest::class) {
        this.projectDependencies.from(runtimeClasspath.incoming.artifactView {
            componentFilter {
                it is ProjectComponentIdentifier
            }
        }.files)
        this.externalDependencies.from(runtimeClasspath.incoming.artifactView {
            componentFilter {
                externalComponents.contains(it)
            }
        }.files)
        this.manifestFile = moduleIdentity.baseName.map { layout.buildDirectory.file("generated-resources/$it-classpath/$it-classpath.properties").get() }
    }
    sourceSets["main"].output.dir(classpathManifest.map { it.manifestFile.get().asFile.parentFile })
}

/**
 * Walk the resolved graph and discover all external dependencies that are not transitive dependencies
 * of project dependencies. This optimizes module loading during runtime, as we will only load external
 * modules that are not loaded transitively by other project modules.
 *
 * We perform this filtering, since if we simply include all external dependencies, regardless of whether
 * they are already loaded transitively, there is a measurable performance impact during module-loading.
 */
fun computeExternalDependenciesNotAccessibleFromProjectDependencies(
    rootComponent: ResolvedComponentResult,
    rootVariant: ResolvedVariantResult
): Set<ComponentIdentifier> {
    val locallyAccessible = mutableSetOf<ComponentIdentifier>()
    val externallyAccessible = mutableSetOf<ComponentIdentifier>()

    val seen = mutableSetOf<ResolvedVariantResult>()
    val queue = ArrayDeque<DependencyResult>()

    val rootDependencies = rootComponent.getDependenciesForVariant(rootVariant)
    seen.add(rootVariant)
    queue.addAll(rootDependencies)

    while (queue.isNotEmpty()) {
        val dependency = when (val result = queue.removeFirst()) {
            is ResolvedDependencyResult -> result
            is UnresolvedDependencyResult -> throw result.failure
            else -> throw AssertionError("Unknown dependency type: $result")
        }

        if (dependency.isConstraint) {
            continue
        }

        val to = dependency.resolvedVariant

        when (val fromComponent = dependency.from.id) {
            is ProjectComponentIdentifier -> if (fromComponent != rootComponent.id) {
                // Only track accessible dependencies from _transitive_ local dependencies
                // We should not include the root variant's dependencies in the locally accessible set
                locallyAccessible.add(to.owner)
            }
            is ModuleComponentIdentifier -> externallyAccessible.add(to.owner)
        }

        if (seen.add(to)) {
            dependency.selected.getDependenciesForVariant(to).forEach {
                queue.add(it)
            }
        }
    }

    val rootModuleComponents = rootDependencies.map { (it as ResolvedDependencyResult).selected.id }
    val candidateExternalComponents = (rootModuleComponents + externallyAccessible).filterIsInstance<ModuleComponentIdentifier>().toSet()
    return candidateExternalComponents - locallyAccessible
}
