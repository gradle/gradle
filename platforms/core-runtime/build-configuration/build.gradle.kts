plugins {
    id("gradlebuild.distribution.api-java")
}

description = "The Build configuration properties modifiers and helpers."

dependencies {
    api(libs.jsr305)
    api(libs.inject)

    api(project(":core"))
    api(project(":core-api"))
    api(project(":jvm-services"))
    api(project(":toolchains-jvm-shared"))
    api(project(":java-language-extensions"))

    implementation(project(":base-services"))
    implementation(project(":logging"))

    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":internal-integ-testing"))

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.  Toolchain usage requires JVM distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
