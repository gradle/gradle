plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "This project contains various native operating system integration utilities"

gradlebuildJava.usedInWorkers()

/**
 * Use Java 8 compatibility for JMH benchmarks
 */
tasks.named<JavaCompile>("jmhCompileGeneratedClasses") {
    options.release = 8
}

dependencies {
    api(projects.serviceLookup)
    api(projects.serviceProvider)
    api(projects.files)

    api(libs.jsr305)
    api(libs.nativePlatform)

    api(projects.baseServices)
    api(projects.fileTemp)

    implementation(projects.stdlibJavaExtensions)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.gradleFileEvents)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsIo)
    implementation(libs.jansi)
    implementation(libs.inject)

    testImplementation(testFixtures(projects.files))
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.logging))

    jmhImplementation(projects.files)
    jmhImplementation(projects.baseServices)
}

jmh {
    fork = 1
    threads = 2
    warmupIterations = 10
    synchronizeIterations = false
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
