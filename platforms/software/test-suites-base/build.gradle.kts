plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Base for test suites.

This project is a implementation dependency of many other testing-related subprojects in the Gradle build.

This project is separate from testing-base to avoid needing to be Java 6 compatible.
"""

dependencies {
    api(projects.javaLanguageExtensions)
    api(project(":base-services"))
    api(project(":core-api"))
    api(project(":model-core"))
    api(project(":platform-base"))

    api(libs.inject)

    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":platform-base")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
}
