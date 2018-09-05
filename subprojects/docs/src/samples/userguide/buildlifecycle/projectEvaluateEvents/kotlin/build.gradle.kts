// tag::after-evaluate[]
allprojects {
    afterEvaluate {
        if (extra["hasTests"] as Boolean) {
            println("Adding test task to $project")
            task("test") {
                doLast {
                    println("Running tests for $project")
                }
            }
        }
    }
}
// end::after-evaluate[]

allprojects {
    extra["hasTests"] = false
}
