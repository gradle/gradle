val sharedConfiguration: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    sharedConfiguration(project(path = ":producer", configuration = "sharedConfiguration"))
}

val sharedFile: File = sharedConfiguration.singleFile

tasks.register("showFile") {
    inputs.file(sharedFile)

    val sharedFileAbsolutePath = sharedFile.absolutePath
    doFirst {
        logger.lifecycle(sharedFileAbsolutePath)
    }
}
