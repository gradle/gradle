val makeFile = tasks.register("makeFile") {
    val sharedFile = layout.buildDirectory.file("some-subdir/shared-file.txt")
    outputs.file(sharedFile)
    doFirst {
        sharedFile.get().asFile.writeText("This file is shared across Gradle subprojects.")
    }
}

val sharedConfiguration by configurations.creating {
    isCanBeResolved = false
}

artifacts {
    add(sharedConfiguration.name, makeFile)
}
