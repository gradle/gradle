plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Worker action that implements RequestHandler worker protocol. " +
    "These classes are loaded in a separate worker daemon process and should have a minimal dependency set."

// TODO: These classes _are_ used in workers, but require Java 8. We should
// enable this flag in Gradle 9.0 when workers target Java 8.
// gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.concurrent)
    api(projects.messaging)
    api(projects.serialization)
    api(projects.workerMain)

    implementation(projects.classloaders)
    implementation(projects.persistentCache)
    implementation(projects.problemsApi)
    implementation(projects.serviceLookup)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)

    // TODO: Ideally, we would not depend on model-core in a worker.
    // All we really want is the instantiation infrastructure, but this
    // brings in core-api, which should be avoided in workers.
    implementation(projects.modelCore)

    implementation(libs.guava)
    implementation(libs.jsr305)
}
