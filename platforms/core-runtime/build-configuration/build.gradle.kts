plugins {
    id("gradlebuild.distribution.api-java")
}

description = "The Build configuration properties modifiers and helpers."

dependencies {
    api(libs.jsr305)
    api(libs.inject)

    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":toolchains-jvm-shared"))
    api(project(":base-annotations"))
    api(project(":platform-jvm"))
    implementation(project(":logging"))
    implementation(project(":jvm-services"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":toolchains-jvm-shared")))

    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":internal-integ-testing"))

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.  Toolchain usage requires JVM distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
