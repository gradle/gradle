tasks.register("performRelease") {
    doLast {
        if (project.hasProperty("isCI")) {
            println("Performing release actions")
        } else {
            throw InvalidUserDataException("Cannot perform release outside of CI")
        }
    }
}
