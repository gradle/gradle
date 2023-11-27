plugins {
    `java-library`
}

// tag::delegated-properties[]
val db by configurations.creating
val integTestImplementation by configurations.creating {
    extendsFrom(configurations["testImplementation"])
}

dependencies {
    db("org.postgresql:postgresql")
    integTestImplementation("com.example:integ-test-support:1.3")
}
// end::delegated-properties[]

// tag::string-reference[]
// get the existing 'testRuntimeOnly' configuration
val testRuntimeOnly by configurations

dependencies {
    testRuntimeOnly("com.example:test-junit-jupiter-runtime:1.3")
    "db"("org.postgresql:postgresql")
    "integTestImplementation"("com.example:integ-test-support:1.3")
}
// end::string-reference[]
