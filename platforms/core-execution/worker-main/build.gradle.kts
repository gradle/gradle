plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Contains the main class that is loaded in a worker process, which is able to execute arbitrary actions. " +
    "These classes are loaded in a separate worker daemon process and should have a minimal dependency set."

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.serviceLookup)
    api(projects.stdlibJavaExtensions)
    api(projects.serialization)
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.logging)
    api(projects.loggingApi)
    api(projects.messaging)
    api(projects.problemsApi)
    api(projects.processMemoryServices)
    api(projects.native)

    api(libs.jspecify)

    implementation(projects.classloaders)
    implementation(projects.concurrent)
    implementation(projects.enterpriseLogging)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.core))
}
