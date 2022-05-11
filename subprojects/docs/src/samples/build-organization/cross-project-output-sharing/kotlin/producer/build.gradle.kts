val sharedFile = project.layout.buildDirectory.file("some-subdir/shared-file.txt")

val makeFile = tasks.register("makeFile") {
    outputs.file(sharedFile)
    doFirst {
        sharedFile.get().asFile.writeText("This file is shared across Gradle subprojects.")
    }
}

val sharedConfiguration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(sharedConfiguration.name, sharedFile)
}
