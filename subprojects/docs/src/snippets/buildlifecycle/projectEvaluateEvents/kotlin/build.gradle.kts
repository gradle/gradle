// tag::after-evaluate[]
gradle.beforeProject {
    // Set a default value
    project.ext.set("hasTests", false)
}

gradle.afterProject {
    if (project.ext.has("hasTests") && project.ext.get("hasTests")) {
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
