plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Adds support for building Groovy projects"

dependencies {
    api(projects.baseServices)
    api(projects.buildOption)
    api(projects.buildProcessServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(projects.fileOperations)
    api(projects.files)
    api(projects.jvmServices)
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

    implementation(projects.classloaders)
    implementation(projects.concurrent)
    implementation(projects.serviceLookup)
    implementation(projects.fileCollections)
    implementation(projects.fileTemp)
    implementation(projects.logging)
    implementation(projects.loggingApi)

    implementation(libs.groovy)
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
    testFixturesImplementation(testFixtures(projects.modelReflect))
    testFixturesImplementation(libs.guava)

    integTestImplementation(testFixtures(projects.modelReflect))
    integTestImplementation(libs.commonsLang)
    integTestImplementation(libs.javaParser) {
        because("The Groovy docs inspects the dependencies at compile time")
    }
    integTestImplementation(libs.nativePlatform) {
        because("Required for SystemInfo")
    }

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

tasks.withType<Test>().configureEach {
    if (!javaVersion.isJava9Compatible) {
        classpath += javaLauncher.get().metadata.installationPath.files("lib/tools.jar")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/compile/**")
    excludePatterns.add("org/gradle/api/tasks/javadoc/**")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
