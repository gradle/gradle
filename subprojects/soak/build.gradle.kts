plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

dependencies {
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":jvm-services"))

    testImplementation(testFixtures(project(":kotlin-dsl")))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":workers")))
    testImplementation(testFixtures(project(":toolchains-jvm")))

    integTestImplementation(project(":file-watching"))
    integTestImplementation(project(":jvm-services"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":persistent-cache"))
    integTestImplementation(libs.commonsCompress)
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.jetty)
    integTestImplementation(libs.assertj) {
        because("Kotlin soak tests use AssertJ")
    }

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

tasks.register("soakTest") {
    description = "Run all soak tests defined in the :soak subproject"
    group = "CI Lifecycle"
    dependsOn(":soak:forkingIntegTest")
}
