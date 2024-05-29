plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Provides high-level insights into a Gradle build (--profile)"

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(projects.time)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":enterprise-logging"))

    implementation(project(":logging"))
    implementation(project(":logging-api"))

    implementation(libs.guava)

    testImplementation(project(":internal-testing"))

    integTestImplementation(libs.jsoup)

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
