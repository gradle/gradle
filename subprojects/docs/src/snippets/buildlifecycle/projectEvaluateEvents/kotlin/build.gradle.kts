// tag::after-evaluate[]
allprojects {
    // Set a default value
    extra["hasTests"] = false

    afterEvaluate {
        if (extra["hasTests"] as Boolean) {
            println("Adding test task to $project")
            tasks.register("test") {
                doLast {
                    println("Running tests for $project")
                }
            }
        }
    }
}
// end::after-evaluate[]
