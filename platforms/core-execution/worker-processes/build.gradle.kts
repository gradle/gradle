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
    api(project(":base-annotations"))
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":logging"))
    api(project(":logging-api"))
    api(project(":messaging"))
    api(project(":problems-api"))
    api(project(":process-services"))
    api("com.google.code.findbugs:jsr305:3.0.2")

    implementation(project(":enterprise-logging"))
    implementation(project(":native"))

    implementation(libs.slf4jApi)

    testImplementation(testFixtures(project(":core")))
}
