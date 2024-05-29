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
    api(projects.serviceProvider)
    api(project(":files"))

    api(libs.jsr305)
    api(libs.nativePlatform)

    api(project(":base-services"))
    api(project(":file-temp"))

    implementation(projects.javaLanguageExtensions)

    implementation(libs.nativePlatformFileEvents)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsIo)
    implementation(libs.jansi)
    implementation(libs.inject)

    testImplementation(testFixtures(projects.files))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    jmhImplementation(project(":files"))
    jmhImplementation(project(":base-services"))
}

jmh {
    fork = 1
    threads = 2
    warmupIterations = 10
    synchronizeIterations = false
}
