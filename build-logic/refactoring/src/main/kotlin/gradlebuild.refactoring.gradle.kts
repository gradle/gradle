import gradlebuild.refactoring.SplitProjectTask

plugins {
    id("java-base")
}

tasks.register<SplitProjectTask>("splitProject") {
    group = "refactoring"
    description = "Splits the project based on provided root classes"

    rootProjectDirectory.set(project.isolated.rootProject.projectDirectory)

    val main = sourceSets.named("main")

    projectClassesDirs.from(main.map { it.output.classesDirs })
    javaSourceDirectories.from(main.map { it.java.sourceDirectories })

    currentProjectDirectory.set(layout.projectDirectory)
    testSourceDirectories.from(listOf(
        sourceSets.named("test"),
        sourceSets.named("integTest")
    ).map { it.map { listOf(
        it.java.sourceDirectories,
        it.extensions.findByType<GroovySourceDirectorySet>()!!.sourceDirectories
    ) } } )

    val resolvedArtifacts = main.flatMap { sourceSet ->
        project.configurations.named(sourceSet.compileClasspathConfigurationName)
    }.flatMap { config ->
        config.incoming.artifacts.resolvedArtifacts
    }

    compileClasspathFiles.set(resolvedArtifacts.map { artifacts ->
        artifacts.map { it.file }
    })
    compileClasspathComponentIds.set(resolvedArtifacts.map { artifacts ->
        artifacts.map { it.id.componentIdentifier }
    })
}
