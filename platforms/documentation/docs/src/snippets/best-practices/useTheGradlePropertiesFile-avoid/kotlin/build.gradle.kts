// tag::avoid-this[]
tasks.register("first") {
    doLast {
        throw GradleException("First task failing as expected")
    }
}

tasks.register("second") {
    doLast {
        logger.lifecycle("Second task succeeding as expected")
    }
}

tasks.register("run") {
    dependsOn("first", "second")
}
// end::avoid-this[]
