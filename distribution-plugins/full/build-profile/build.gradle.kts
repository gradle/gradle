plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Provides high-level insights into a Gradle build (--profile)"

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:messaging")
    implementation("org.gradle:logging")
    implementation("org.gradle:core-api")
    implementation("org.gradle:core")
    implementation("org.gradle:build-option")

    implementation(libs.guava)

    testImplementation("org.gradle:internal-testing")

    integTestImplementation(libs.jsoup)

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
