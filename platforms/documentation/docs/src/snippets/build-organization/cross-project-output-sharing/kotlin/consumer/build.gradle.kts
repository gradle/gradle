val sharedConfiguration: Configuration by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    sharedConfiguration(project(path = ":producer", configuration = "sharedConfiguration"))
}

tasks.register("showFile") {
    val sharedFiles: FileCollection = sharedConfiguration
    inputs.files(sharedFiles)
    doFirst {
        logger.lifecycle("Shared file contains the text: '{}'", sharedFiles.singleFile.readText())
    }
}
