plugins {
    `java-library`
}

tasks.register("myCheck")

// tag::string-invoke[]
tasks {
    "test"(Test::class) {
        testLogging.showStackTraces = true
    }
    "check" {
        dependsOn(named("myCheck"))
    }
}
// end::string-invoke[]
