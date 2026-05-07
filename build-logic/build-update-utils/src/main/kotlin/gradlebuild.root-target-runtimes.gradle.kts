import gradlebuild.buildutils.runtimes.CheckTargetRuntimes
import gradlebuild.configureAsRuntimeJarClasspath

plugins {
    id("gradlebuild.repositories")
}

val runtimeAware: NamedDomainObjectProvider<DependencyScopeConfiguration> = configurations.dependencyScope("runtimeAware") {
    description = "All dependencies which should run in a Gradle context, and therefore must declare or inherit target runtime compatibility"
}

// Computes a map of all projects in the full Gradle distribution to their target runtime details file.
val targetRuntimeDetails: Provider<Map<String, File>> = configurations.resolvable("fullDistributionRuntimeClasspath") {
    extendsFrom(runtimeAware)
    configureAsRuntimeJarClasspath(objects)
}.flatMap {
    it.incoming.artifactView {
        withVariantReselection()
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named("target-runtime-details"))
            attribute(Usage.USAGE_ATTRIBUTE, objects.named("target-runtime-details"))
        }
    }.artifacts.resolvedArtifacts.map { artifacts ->
        artifacts.mapNotNull { artifact ->
            when (val id = artifact.id.componentIdentifier) {
                is ProjectComponentIdentifier -> when {
                    // Currently, target runtime tracking is not supported for included builds
                    id.projectPath == id.buildTreePath -> {
                        id.buildTreePath to artifact.file
                    }
                    else -> null
                }
                else -> throw IllegalArgumentException("Unexpected component identifier: $id")
            }
        }.toMap()
    }
}

// Computes a map of all projects in the build to their build file.
val buildFiles: Provider<Map<String, File>> = provider {
    allprojects.associate { project ->
        project.buildTreePath to project.buildFile
    }
}

tasks.register<CheckTargetRuntimes>("checkTargetRuntimes") {
    projectPaths = targetRuntimeDetails.map { it.map { it.key }}
    targetRuntimeDetailsFiles = targetRuntimeDetails.map { it.map { it.value }}
    projectBuildFiles = targetRuntimeDetails.zip(buildFiles) { projectDetails, buildFiles ->
        projectDetails.map { buildFiles[it.key] ?: error("Could not find build file for project ${it.key}") }
    }
}
