import gradlebuild.buildutils.runtimes.CheckTargetRuntimes
import gradlebuild.buildutils.runtimes.ProjectBuildFileDetails
import gradlebuild.buildutils.runtimes.ProjectTargetRuntimeDetails

val allSubprojects = configurations.dependencyScope("allSubprojects") {
    dependencies.addAllLater(provider {
        allprojects.map { project.dependencies.create(it) }
    })
}

val allSubprojectsTargetRuntimeDetails = configurations.resolvable("allSubprojectsTargetRuntimeDetails") {
    extendsFrom(allSubprojects.get())
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("target-runtime-details"))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("target-runtime-details"))
    }
}

val targetRuntimeDetails = allSubprojectsTargetRuntimeDetails.flatMap {
    it.incoming.artifactView {
        isLenient = true // Some projects may not have target runtimes declared.
    }.artifacts.resolvedArtifacts.map { artifacts ->
        artifacts.mapNotNull { artifact ->
            when (val id = artifact.id.componentIdentifier) {
                is ProjectComponentIdentifier -> when {
                    id.projectPath == id.buildTreePath -> { // Currently targetRuntimes are not supported for included builds
                        ProjectTargetRuntimeDetails(
                            id.buildTreePath,
                            artifact.file
                        )
                    }
                    else -> null
                }
                else -> throw IllegalArgumentException("Unexpected component identifier: $id")
            }
        }
    }
}

val buildFiles = provider {
    allprojects.map { project ->
        ProjectBuildFileDetails(
            projectPath = project.path,
            buildFile = project.buildFile,
        )
    }
}

tasks.register<CheckTargetRuntimes>("checkTargetRuntimes") {
    dependsOn(targetRuntimes) // TODO: THIS IS WRONG ISH
    targetRuntimes = targetRuntimeDetails
    projectBuildFiles = buildFiles
}
