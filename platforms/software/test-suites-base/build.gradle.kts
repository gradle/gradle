plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Base for test suites.

This project is a implementation dependency of many other testing-related subprojects in the Gradle build.
"""

// TODO: This project should be merged with testing-base. It was separated for historical reasons that are no longer relevant.

dependencies {
    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.modelCore)
    api(projects.platformBase)
    api(projects.stdlibJavaExtensions)
    api(projects.testingBase)

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
