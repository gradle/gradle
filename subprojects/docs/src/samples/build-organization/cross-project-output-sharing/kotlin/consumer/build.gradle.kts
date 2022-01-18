val sharedConfiguration: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    sharedConfiguration(project(path = ":producer", configuration = "sharedConfiguration"))
}

tasks.register("showFile") {
    inputs.files(sharedConfiguration)
    doFirst {
        logger.lifecycle(sharedConfiguration.singleFile.absolutePath)
    }
}
