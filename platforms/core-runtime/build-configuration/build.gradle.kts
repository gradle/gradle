plugins {
    id("gradlebuild.distribution.api-java")
}

description = "The Build configuration properties modifiers and helpers."

dependencies {
    api(libs.jsr305)
    api(libs.inject)

    api(projects.core)
    api(projects.coreApi)
    api(projects.jvmServices)
    api(projects.toolchainsJvmShared)
    api(projects.stdlibJavaExtensions)

    implementation(projects.baseServices)
    implementation(projects.daemonProtocol)
    implementation(projects.logging)

    testImplementation(testFixtures(projects.core))

    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.internalIntegTesting)

    testRuntimeOnly(projects.distributionsJvm) {
        because("ProjectBuilder tests load services from a Gradle distribution.  Toolchain usage requires JVM distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
