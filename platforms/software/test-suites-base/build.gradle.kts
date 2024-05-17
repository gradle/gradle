plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Base for test suites.

This project is a implementation dependency of many other testing-related subprojects in the Gradle build.

This project is separate from testing-base to avoid needing to be Java 6 compatible.
"""

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":platform-base"))
    implementation(project(":testing-base"))

    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":base-services")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
}
