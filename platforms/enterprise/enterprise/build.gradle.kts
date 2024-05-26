plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Services and utilities needed by Develocity plugin"

errorprone {
    disabledChecks.addAll(
        "SameNameButDifferent", // 4 occurrences
    )
}

tasks.isolatedProjectsIntegTest {
    enabled = true
}

dependencies {
    api(projects.serviceProvider)
    api(project(":build-operations"))
    api(project(":base-services"))
    api(project(":configuration-cache"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":daemon-services"))
    api(project(":enterprise-logging"))
    api(project(":file-collections"))
    api(project(":java-language-extensions"))
    api(project(":jvm-services"))
    api(project(":launcher"))
    api(project(":model-core"))
    api(project(":snapshots"))
    api(project(":testing-jvm"))
    api(project(":time"))
    api(project(":problems-api"))

    api(libs.inject)
    api(libs.jsr305)

    implementation(project(":concurrent"))
    implementation(project(":dependency-management"))
    implementation(project(":files"))
    implementation(project(":hashing"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":serialization"))
    implementation(project(":testing-base"))

    implementation(libs.guava)

    compileOnly(libs.groovy) {
        because("some used APIs (e.g. FileTree.visit) provide methods taking Groovy closures which causes compile errors")
    }

    testImplementation(project(":resources"))

    integTestImplementation(project(":internal-testing"))
    integTestImplementation(project(":internal-integ-testing"))
    integTestImplementation(testFixtures(project(":core")))

    // Dependencies of the integ test fixtures
    integTestImplementation(project(":build-option"))
    integTestImplementation(project(":messaging"))
    integTestImplementation(project(":persistent-cache"))
    integTestImplementation(project(":native"))
    integTestImplementation(testFixtures(project(":problems-api")))
    integTestImplementation(libs.guava)

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
