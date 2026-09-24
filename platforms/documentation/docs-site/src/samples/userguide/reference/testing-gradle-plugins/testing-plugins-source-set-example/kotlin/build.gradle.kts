plugins {
    id("java")
}

// tag::source-set-example[]
// Define a source set named 'test' for test sources
sourceSets {
    test {
        java {
            setSrcDirs(listOf("src/test/java"))
        }
    }
}
// Specify a test implementation dependency on JUnit
dependencies {
    testImplementation("junit:junit:4.12")
}
// end::source-set-example[]
