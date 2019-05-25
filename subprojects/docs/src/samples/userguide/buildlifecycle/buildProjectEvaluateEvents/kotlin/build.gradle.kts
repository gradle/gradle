// tag::evaluate-events[]
gradle.afterProject {
    if (state.failure != null) {
        println("Evaluation of $project FAILED")
    } else {
        println("Evaluation of $project succeeded")
    }
}
// end::evaluate-events[]

tasks.register("test")
