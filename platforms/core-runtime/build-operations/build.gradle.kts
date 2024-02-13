plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Build operations are our way to inspect the process of executing a build"

gradlebuildJava.usedInWorkers()

errorprone {
    disabledChecks.addAll(
        "ThreadLocalUsage", // 1 occurrences
    )
}

dependencies {
    api(libs.jsr305)
    api(project(":base-annotations"))

    implementation(libs.slf4jApi)
}
