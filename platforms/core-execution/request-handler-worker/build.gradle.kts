plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Worker action that implements RequestHandler worker protocol. " +
    "These classes are loaded in a separate worker daemon process and should have a minimal dependency set."

gradleModule {
    targetRuntimes {
        usedInWorkers = true
    }
}

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.concurrent)
    api(projects.messaging)
    api(projects.serialization)
    api(projects.workerMain)

    // TODO: Ideally, we would not depend on model-core in a worker.
    // All we really want is the instantiation infrastructure, but this
    // brings in core-api, which should be avoided in workers.
    api(projects.messaging)
    api(projects.serialization)
    api(projects.workerMain)

    implementation(libs.jspecify)

    implementation(projects.modelCore)
    implementation(projects.persistentCache)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.serviceLookup)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.guava)

    runtimeOnly(projects.coreApi)
}
