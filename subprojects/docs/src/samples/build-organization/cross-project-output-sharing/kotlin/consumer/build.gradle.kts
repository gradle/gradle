val sharedConfiguration: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    sharedConfiguration(project(path = ":producer", configuration = "sharedConfiguration"))
}

tasks.register("showFile") {
    val sharedFiles: FileCollection = sharedConfiguration
    inputs.files(sharedFiles)
    doFirst {
        logger.lifecycle("File is at {}", sharedFiles.singleFile.absolutePath)
    }
}
