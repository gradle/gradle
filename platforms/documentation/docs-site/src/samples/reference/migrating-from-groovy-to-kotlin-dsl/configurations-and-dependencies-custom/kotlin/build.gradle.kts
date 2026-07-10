plugins {
    `java-library`
}

// tag::direct-reference[]
val db = configurations.create("db")
val integTestImplementation = configurations.create("integTestImplementation") {
    extendsFrom(configurations["testImplementation"])
}

dependencies {
    db("org.postgresql:postgresql")
    integTestImplementation("com.example:integ-test-support:1.3")
}
// end::direct-reference[]

// tag::string-reference[]
// get the existing 'testRuntimeOnly' configuration
val testRuntimeOnly = configurations["testRuntimeOnly"]

dependencies {
    testRuntimeOnly("com.example:test-junit-jupiter-runtime:1.3")
    "db"("org.postgresql:postgresql")
    "integTestImplementation"("com.example:integ-test-support:1.3")
}
// end::string-reference[]
