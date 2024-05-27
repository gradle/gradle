plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Infrastructure that bootstraps a worker process"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serialization)
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":logging"))
    api(project(":logging-api"))
    api(project(":messaging"))
    api(project(":problems-api"))
    api(project(":process-services"))
    api(project(":native"))
    api(libs.jsr305)

    implementation(projects.concurrent)
    implementation(project(":enterprise-logging"))
    implementation(projects.serviceProvider)

    implementation(libs.slf4jApi)

    testImplementation(testFixtures(project(":core")))
}
