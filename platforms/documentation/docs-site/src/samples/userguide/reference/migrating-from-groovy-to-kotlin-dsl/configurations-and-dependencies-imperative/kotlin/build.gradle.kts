apply(plugin = "java-library")
dependencies {
    "implementation"("com.example:lib:1.1")
    "runtimeOnly"("com.example:runtime:1.0")
    "testImplementation"("com.example:test-support:1.3") {
        exclude(module = "junit")
    }
    "testRuntimeOnly"("com.example:test-junit-jupiter-runtime:1.3")
}
