plugins {
    id("java")
}

tasks.register<JavaExec>("myRunTask") {
    jvmArgumentProviders.clear() // Clear existing JVM argument providers
    maxHeapSize = null // Clear max heap size
    jvmArgs = listOf("-Dfoo", "-Dbar") // Set new JVM arguments
}
