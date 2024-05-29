plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Process execution abstractions."

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.concurrent)
    api(projects.javaLanguageExtensions)
    api(project(":base-services"))
    api(project(":messaging"))

    api(libs.jsr305)

    implementation(project(":native"))

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.nativePlatform)

    testImplementation(testFixtures(project(":core")))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

packageCycles {
    excludePatterns.add("org/gradle/process/internal/**")
}
