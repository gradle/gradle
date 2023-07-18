plugins {
    id("gradlebuild.internal.kotlin")
}

dependencies {
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":jvm-services"))

    testImplementation(testFixtures(project(":kotlin-dsl")))
    testImplementation(testFixtures(project(":core")))

    integTestImplementation(project(":file-watching"))
    integTestImplementation(project(":jvm-services"))
    integTestImplementation(project(":launcher"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":persistent-cache"))
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
