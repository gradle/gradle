import gradlebuild.testing.isUnsupportedHegelTestPlatform

plugins {
    id("gradlebuild.internal.java")
}

description = "Hegel property-based testing for JUnit Jupiter tests, internal use only"

dependencies {
    implementation(testLibs.hegelJna)

    compileOnly(libs.jspecify)
    // Every test runtime provides JUnit Platform
    compileOnly(testLibs.junitPlatform)
    compileOnly(testLibs.junitPlatformCommons)
    compileOnly(testLibs.junitPlatformEngine)

    runtimeOnly(testLibs.junit5JupiterEngine)

    testImplementation(testLibs.junit5JupiterApi)
    testImplementation(testLibs.junitPlatformTestkit)
}

tasks.test {
    // The self-tests run their fixtures through EngineTestKit
    filter.excludeTestsMatching("org.gradle.test.fixtures.hegel.fixtures.*")

    // Every test here runs Hegel
    val unsupportedPlatform = isUnsupportedHegelTestPlatform()
    onlyIf("Hegel native library available") {
        !unsupportedPlatform
    }
}
