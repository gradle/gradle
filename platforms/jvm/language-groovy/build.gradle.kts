plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Adds support for building Groovy projects"

dependencies {
    api(projects.baseCompilerWorker)
    api(projects.buildOption)
    api(projects.buildProcessServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(projects.fileOperations)
    api(projects.files)
    api(projects.groovyCompilerWorker)
    api(projects.groovydocWorker)
    api(projects.javaCompilerWorker)
    api(projects.jvmServices)
    api(projects.jvmCompilerWorker)
    api(projects.languageJava)
    api(projects.languageJvm)
    api(projects.platformBase)
    api(projects.problemsApi)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)
    api(projects.toolchainsJvm)
    api(projects.toolchainsJvmShared)
    api(projects.workerMain)
    api(projects.workers)

    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.baseServices)
    implementation(projects.classloaders)
    implementation(projects.fileCollections)
    implementation(projects.fileTemp)
    implementation(projects.logging)
    implementation(projects.loggingApi)
    implementation(projects.serviceLookup)

    implementation(libs.guava)
    implementation(libs.asm)

    testImplementation(projects.baseServicesGroovy)
    testImplementation(projects.internalTesting)
    testImplementation(projects.resources)
    testImplementation(testFixtures(projects.core))

    testFixturesApi(testFixtures(projects.languageJvm))
    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(testFixtures(projects.core))
    testFixturesImplementation(testFixtures(projects.modelReflect))
    testFixturesImplementation(testFixtures(projects.testingBase))

    testFixturesImplementation(libs.guava)

    integTestImplementation(testFixtures(projects.modelReflect))
    integTestImplementation(testFixtures(projects.testingBase))
    integTestImplementation(libs.commonsLang)
    integTestImplementation(libs.nativePlatform) {
        because("Required for SystemInfo")
    }

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/compile/**")
    excludePatterns.add("org/gradle/api/tasks/javadoc/**")
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
