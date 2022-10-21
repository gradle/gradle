// tag::after-evaluate[]
gradle.beforeProject {
    // Set a default value
    extra["hasTests"] = false
}

gradle.afterProject {
    if (extra["hasTests"] as Boolean) {
        val projectString = project.toString()
        println("Adding test task to $projectString")
        tasks.register("test") {
            doLast {
                println("Running tests for $projectString")
            }
        }
    }
}
// end::after-evaluate[]
