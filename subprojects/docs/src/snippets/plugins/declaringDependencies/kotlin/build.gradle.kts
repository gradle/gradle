// tag::declaring_dependencies_block[]
plugins {
    java
    `jvm-test-suite`
}

dependencies { // <1>
    implementation(plugin("com.example.hello", "1.0.0"))
    implementation(plugin("com.example.goodbye"))
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            dependencies { // <2>
                implementation.plugin("com.example.hello", "1.0.0")
                implementation.plugin("com.example.goodbye")
            }
        }
    }
}
// end::declaring_dependencies_block[]
