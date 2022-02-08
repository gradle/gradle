val sharedFile: Provider<RegularFile> = project.layout.buildDirectory.dir("some-subdir").map { it.file("shared-file.txt") }

val makeFile = tasks.register("makeFile") {
    outputs.file(sharedFile)

    val fileToWrite = sharedFile.get().asFile
    doFirst {
        fileToWrite.writeText("This file is shared across Gradle subprojects.")
    }
}

val sharedConfiguration: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(sharedConfiguration.name, makeFile)
}
