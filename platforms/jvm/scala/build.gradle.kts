plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins for building Scala code with Gradle."

dependencies {
    api(projects.jvmCompilerWorker)
    api(projects.baseServices)
    api(projects.buildProcessServices)
    api(projects.classloaders)
    api(projects.core)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(projects.fileOperations)
    api(projects.files)
    api(projects.languageJvm)
    api(projects.loggingApi)
    api(projects.modelCore)
    api(projects.platformJvm)
    api(projects.scalaCompilerWorker)
    api(projects.stdlibJavaExtensions)
    api(projects.toolchainsJvm)
    api(projects.toolchainsJvmShared)
    api(projects.workers)

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.dependencyManagement)
    implementation(projects.fileCollections)
    implementation(projects.javaCompilerWorker)
    implementation(projects.jvmServices)
    implementation(projects.languageJava)
    implementation(projects.logging)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJavaBase)
    implementation(projects.reporting)
    implementation(projects.serviceLookup)
    implementation(projects.workerMain)

    implementation(libs.guava)

    testImplementation(projects.baseServicesGroovy)
    testImplementation(projects.files)
    testImplementation(projects.resources)
    testImplementation(libs.slf4jApi)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.pluginsJava))
    testImplementation(testFixtures(projects.languageJvm))
    testImplementation(testFixtures(projects.languageJava))

    integTestImplementation(projects.jvmServices)

    testFixturesImplementation(testFixtures(projects.languageJvm))

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/scala/**")
    excludePatterns.add("org/gradle/api/tasks/*")
    excludePatterns.add("org/gradle/api/tasks/scala/internal/*")
    excludePatterns.add("org/gradle/language/scala/tasks/*")
}

integTest.usesJavadocCodeSnippets = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
