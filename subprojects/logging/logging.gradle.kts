plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Logging infrastructure"

gradlebuildJava.usedInWorkers()

dependencies {
    api(libs.slf4j_api)

    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":cli"))
    implementation(project(":buildOption"))

    implementation(project(":native"))
    implementation(libs.jul_to_slf4j)
    implementation(libs.ant)
    implementation(libs.commons_lang)
    implementation(libs.guava)
    implementation(libs.jansi)

    runtimeOnly(libs.log4j_to_slf4j)
    runtimeOnly(libs.jcl_to_slf4j)

    testImplementation(testFixtures(project(":core")))

    integTestImplementation(libs.ansi_control_sequence_util)

    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(libs.slf4j_api)

    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}

classycle {
    excludePatterns.set(listOf("org/gradle/internal/featurelifecycle/**"))
}
