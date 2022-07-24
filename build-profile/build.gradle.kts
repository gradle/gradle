plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Provides high-level insights into a Gradle build (--profile)"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":build-option"))

    implementation(libs.guava)

    testImplementation(project(":internal-testing"))

    integTestImplementation(libs.jsoup)

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
