plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Base for test suites.

This project is a implementation dependency of many other testing-related subprojects in the Gradle build.

This project is separate from testing-base to avoid needing to be Java 6 compatible.
"""

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.modelCore)
    api(projects.platformBase)

    api(libs.inject)

    testImplementation(testFixtures(projects.baseServices))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))
    testImplementation(testFixtures(projects.messaging))
    testImplementation(testFixtures(projects.platformBase))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
