// TODO not exactly the same as groovy: how to handle the second argument in a type-safe way?
// tag::evaluate-events[]
gradle.afterProject {
    if (state.failure != null) {
        println("Evaluation of $project FAILED")
    } else {
        println("Evaluation of $project succeeded")
    }
}
// end::evaluate-events[]

task("test")
