plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Adds support for building Groovy projects"

errorprone {
    disabledChecks.addAll(
        "ModifyCollectionInEnhancedForLoop", // 1 occurrences
        "UnusedMethod", // 4 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.buildOption)
    api(projects.coreApi)
    api(projects.core)
    api(projects.files)
    api(projects.fileTemp)
    api(projects.jvmServices)
    api(projects.languageJava)
    api(projects.languageJvm)
    api(projects.problemsApi)
    api(projects.platformBase)
    api(projects.toolchainsJvm)
    api(projects.toolchainsJvmShared)
    api(projects.workers)
    api(projects.workerMain)
    api(projects.buildProcessServices)

    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.concurrent)
    implementation(projects.serviceLookup)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.fileCollections)
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
    testFixturesImplementation(testFixtures(projects.modelCore))
    testFixturesImplementation(libs.guava)

    integTestImplementation(testFixtures(projects.modelCore))
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
