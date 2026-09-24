plugins {
    `java-library`
}

// tag::scope[]
tasks {
    test {
        testLogging.showStackTraces = true
    }
    val myCheck = register("myCheck") {
        doLast { /* assert on something meaningful */ }
    }
    check {
        dependsOn(myCheck)
    }
    register("myHelp") {
        doLast { /* do something helpful */ }
    }
}
// end::scope[]
