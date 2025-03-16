plugins {
    id("gradlebuild.distribution.implementation-java")
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
    api(projects.hashing)
    // TODO: Ideally, we would not depend on model-core in a worker.
    // All we really want is the instantiation infrastructure, but this
    // brings in core-api, which should be avoided in workers.
    api(projects.modelCore)
    api(projects.messaging)
    api(projects.problemsApi)
    api(projects.serialization)
    api(projects.snapshotsWorker)
    api(projects.stdlibJavaExtensions)
    api(projects.workerMain)

    api(libs.jspecify)

    implementation(projects.classloaders)
    implementation(projects.coreApi)
    implementation(projects.persistentCache)
    implementation(projects.serviceLookup)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)
    implementation(projects.snapshots)

    implementation(libs.guava)
}
