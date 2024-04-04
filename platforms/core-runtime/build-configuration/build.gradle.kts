plugins {
    id("gradlebuild.distribution.api-java")
}

description = "The Build configuration properties modifiers and helpers."

dependencies {
    api(libs.inject)
    api(libs.jsr305)

    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":jvm-services"))
    api(project(":toolchains-jvm-shared"))

    implementation(project(":base-annotations"))

    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":internal-integ-testing"))

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.  Toolchain usage requires JVM distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
