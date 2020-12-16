plugins {
    id("gradlebuild.internal.kotlin")
}

dependencies {
    testFixturesImplementation("org.gradle:base-services")
    testFixturesImplementation("org.gradle:core")
    testFixturesImplementation("org.gradle:internal-integ-testing")
    testFixturesImplementation("org.gradle:jvm-services")

    testImplementation(testFixtures("org.gradle:kotlin-dsl"))
    testImplementation(testFixtures("org.gradle:core"))

    integTestImplementation("org.gradle:file-watching")
    integTestImplementation("org.gradle:jvm-services")
    integTestImplementation("org.gradle:launcher")
    integTestImplementation("org.gradle:logging")
    integTestImplementation("org.gradle:persistent-cache")
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.jetty)

    integTestDistributionRuntimeOnly("org.gradle:distributions-full")
}

tasks.register("soakTest") {
    description = "Run all soak tests defined in the :soak subproject"
    group = "CI Lifecycle"
    dependsOn(tasks.forkingIntegTest)
}
