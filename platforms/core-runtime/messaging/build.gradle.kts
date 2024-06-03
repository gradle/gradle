plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Implementation of messaging between Gradle processes"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.concurrent)
    api(projects.javaLanguageExtensions)
    api(projects.serialization)
    api(projects.serviceProvider)
    api(project(":base-services"))

    api(libs.jsr305)
    api(libs.slf4jApi)

    implementation(projects.io)
    implementation(project(":build-operations"))

    implementation(libs.guava)

    testImplementation(testFixtures(projects.serialization))
    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(libs.slf4jApi)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
