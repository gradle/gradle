plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Infrastructure that bootstraps a worker process"

gradlebuildJava.usedInWorkers()

errorprone {
    disabledChecks.addAll(
        "UnusedMethod", // 6 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
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

    implementation(libs.slf4jApi)

    testImplementation(testFixtures(project(":core")))
}
