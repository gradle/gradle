plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Execution engine that takes a unit of work and makes it happen"

dependencies {
    api(libs.guava)
    api(libs.jsr305)
    api(libs.slf4jApi)

    api(project(":base-annotations"))
    api(project(":base-services"))
    api(project(":build-cache"))
    api(project(":build-cache-base"))
    api(project(":build-operations"))
    api(project(":core-api"))
    api(project(":files"))
    api(project(":functional"))
    api(project(":hashing"))
    api(project(":messaging"))
    api(project(":model-core"))
    api(project(":persistent-cache"))
    api(project(":problems-api"))
    api(project(":snapshots"))

    implementation(project(":logging"))
    implementation(projects.enterpriseOperations) {
        because("Adds generic build operations for the execution engine")
    }

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)

    testImplementation(project(":native"))
    testImplementation(project(":logging"))
    testImplementation(project(":process-services"))
    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":resources"))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":file-collections")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":snapshots")))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))

    testFixturesImplementation(libs.guava)
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":build-cache"))
    testFixturesImplementation(project(":problems"))
    testFixturesImplementation(project(":snapshots"))

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
