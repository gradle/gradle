plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Infrastructure for starting and managing worker processes"

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.buildProcessServices)
    api(projects.classloaders)
    api(projects.concurrent)
    api(projects.core)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.messaging)
    api(projects.modelCore)
    api(projects.processMemoryServices)
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)
    api(projects.workerMain)

    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.hashing)
    implementation(projects.requestHandlerWorker)
    implementation(projects.serialization)
    implementation(projects.snapshots)
    implementation(projects.time)

    implementation(libs.jsr305)
    implementation(libs.slf4jApi)
    implementation(libs.guava)

    testImplementation(projects.native)
    testImplementation(projects.fileCollections)
    testImplementation(projects.resources)
    testImplementation(projects.snapshots)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))

    integTestRuntimeOnly(projects.kotlinDsl)
    integTestRuntimeOnly(projects.kotlinDslProviderPlugins)
    integTestRuntimeOnly(projects.apiMetadata)
    integTestRuntimeOnly(projects.testKit)

    integTestImplementation(projects.jvmServices)
    integTestImplementation(projects.enterpriseOperations)

    testFixturesImplementation(libs.inject)
    testFixturesImplementation(libs.groovyJson)
    testFixturesImplementation(projects.baseServices)

    integTestDistributionRuntimeOnly(projects.distributionsJvm) {
        because("Uses application plugin.")
    }
}

integTest.usesJavadocCodeSnippets = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
