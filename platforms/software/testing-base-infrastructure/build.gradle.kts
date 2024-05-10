plugins {
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedInWorkers()

description = """Generalized test infrastructure to support executing tests in test workers."""

dependencies {
    api(projects.concurrent)
    api(projects.javaLanguageExtensions)
    api(projects.serialization)
    api(projects.time)
    api(project(":base-services"))
    api(project(":logging"))
    api(project(":logging-api"))
    api(project(":worker-processes"))

    api(libs.jsr305)

    implementation(projects.io)
    implementation(projects.messaging)

    implementation(libs.commonsLang)
    implementation(libs.slf4jApi)

    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(projects.serialization))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/testing/**")
}

integTest.usesJavadocCodeSnippets = true
