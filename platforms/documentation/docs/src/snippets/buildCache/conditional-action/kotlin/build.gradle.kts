plugins {
    id("java-library")
}

// tag::conditionalAction[]
if ("CI" in System.getenv()) {
    tasks.withType<Test>().configureEach {
        doFirst {
            println("Running test on CI")
        }
    }
}
// end::conditionalAction[]

// tag::unconditionalAction[]
tasks.withType<Test>().configureEach {
    doFirst {
        if ("CI" in System.getenv()) {
            println("Running test on CI")
        }
    }
}
// end::unconditionalAction[]
