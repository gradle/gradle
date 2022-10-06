// tag::after-evaluate[]
allprojects {
    // Set a default value
    extra["hasTests"] = false

    afterEvaluate {
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
}
// end::after-evaluate[]
