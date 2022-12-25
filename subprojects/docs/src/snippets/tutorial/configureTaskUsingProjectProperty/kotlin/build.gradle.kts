tasks.register("performRelease") {
    val isCI = providers.gradleProperty("isCI")
    doLast {
        if (isCI.isPresent) {
            println("Performing release actions")
        } else {
            throw InvalidUserDataException("Cannot perform release outside of CI")
        }
    }
}
