plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Source for JavaCompile, JavaExec and Javadoc tasks, it also contains logic for incremental Java compilation"

errorprone {
    disabledChecks.addAll(
        "CheckReturnValue", // 2 occurrences
        "InconsistentCapitalization", // 1 occurrences
        "InvalidInlineTag", // 3 occurrences
    )
}

dependencies {
    api(projects.baseServices)
    api(projects.buildEvents)
    api(projects.buildOperations)
    api(projects.buildProcessServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(projects.dependencyManagement)
    api(projects.fileCollections)
    api(projects.fileOperations)
    api(projects.files)
    api(projects.hashing)
    api(projects.javaCompilerWorker)
    api(projects.jvmCompilerWorker)
    api(projects.jvmServices)
    api(projects.languageJvm)
    api(projects.modelCore)
    api(projects.persistentCache)
    api(projects.platformJvm)
    api(projects.problemsApi)
    api(projects.processServices)
    api(projects.serialization)
    api(projects.serviceProvider)
    api(projects.snapshots)
    api(projects.stdlibJavaExtensions)
    api(projects.testSuitesBase)
    api(projects.toolchainsJvm)
    api(projects.toolchainsJvmShared)
    api(projects.workerMain)
    api(projects.workers)

    api(libs.asm)
    api(libs.fastutil)
    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)
    api(libs.slf4jApi)

    implementation(projects.classloaders)
    implementation(projects.fileTemp)
    implementation(projects.logging)
    implementation(projects.loggingApi)
    implementation(projects.platformBase)
    implementation(projects.serviceLookup)
    implementation(projects.time)
    implementation(projects.toolingApi)

    implementation(libs.ant)
    implementation(libs.commonsCompress)
    implementation(libs.commonsLang)
    implementation(libs.guava)

    runtimeOnly(projects.javaCompilerPlugin)

    testImplementation(projects.baseServicesGroovy)
    testImplementation(projects.native)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.platformBase))
    testImplementation(testFixtures(projects.toolchainsJvm))
    testImplementation(testFixtures(projects.toolchainsJvmShared))

    testImplementation(libs.commonsIo)
    testImplementation(libs.nativePlatform) {
        because("Required for SystemInfo")
    }

    integTestImplementation(projects.messaging)
    // TODO: Make these available for all integration tests? Maybe all tests?
    integTestImplementation(libs.jetbrainsAnnotations)
    integTestImplementation(libs.commonsHttpclient)

    testFixturesApi(testFixtures(projects.languageJvm))
    testFixturesImplementation(projects.baseServices)
    testFixturesImplementation(projects.enterpriseOperations)
    testFixturesImplementation(projects.core)
    testFixturesImplementation(projects.coreApi)
    testFixturesImplementation(projects.modelCore)
    testFixturesImplementation(projects.internalIntegTesting)
    testFixturesImplementation(projects.platformBase)
    testFixturesImplementation(projects.persistentCache)
    testFixturesImplementation(libs.slf4jApi)

    testRuntimeOnly(projects.distributionsCore) {
        because("ProjectBuilder test (JavaLanguagePluginTest) loads services from a Gradle distribution.")
    }

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
    crossVersionTestDistributionRuntimeOnly(projects.distributionsBasics)
}

tasks.withType<Test>().configureEach {
    if (!javaVersion.isJava9Compatible) {
        classpath += javaLauncher.get().metadata.installationPath.files("lib/tools.jar")
    }
}

strictCompile {
    ignoreDeprecations() // this project currently uses many deprecated part from 'platform-jvm'
}

packageCycles {
    // These public packages have classes that are tangled with the corresponding internal package.
    excludePatterns.add("org/gradle/api/tasks/**")
    excludePatterns.add("org/gradle/external/javadoc/**")
}

integTest.usesJavadocCodeSnippets = true

tasks.isolatedProjectsIntegTest {
    enabled = false
}
