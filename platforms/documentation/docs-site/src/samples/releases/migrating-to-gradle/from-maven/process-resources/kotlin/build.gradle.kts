tasks {
    processResources {
        expand("version" to version, "buildNumber" to currentBuildNumber)
    }
}
