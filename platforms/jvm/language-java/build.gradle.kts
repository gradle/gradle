plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Source for JavaCompile, JavaExec and Javadoc tasks, it also contains logic for incremental Java compilation"

errorprone {
    disabledChecks.addAll(
        "CheckReturnValue", // 2 occurrences
        "DoNotClaimAnnotations", // 6 occurrences
        "InconsistentCapitalization", // 1 occurrences
        "InvalidInlineTag", // 3 occurrences
        "MissingCasesInEnumSwitch", // 1 occurrences
        "MixedMutabilityReturnType", // 3 occurrences
        "OperatorPrecedence", // 2 occurrences
    )
}

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.serialization)
    api(projects.serviceProvider)
    api(projects.baseServices)
    api(projects.buildEvents)
    api(projects.buildOperations)
    api(projects.core)
    api(projects.coreApi)
    api(projects.dependencyManagement)
    api(projects.fileCollections)
    api(projects.files)
    api(projects.hashing)
    api(projects.languageJvm)
    api(projects.persistentCache)
    api(projects.platformBase)
    api(projects.platformJvm)
    api(projects.problemsApi)
    api(projects.processServices)
    api(projects.snapshots)
    api(projects.testSuitesBase)
    api(projects.toolchainsJvm)
    api(projects.toolchainsJvmShared)
    api(projects.workerMain)
    api(projects.workers)
    api(projects.buildProcessServices)

    api(libs.asm)
    api(libs.fastutil)
    api(libs.groovy)
    api(libs.guava)
    api(libs.jsr305)
    api(libs.inject)

    implementation(projects.concurrent)
    implementation(projects.serviceLookup)
    implementation(projects.time)
    implementation(projects.fileTemp)
    implementation(projects.loggingApi)
    implementation(projects.modelCore)
    implementation(projects.toolingApi)

    api(libs.slf4jApi)
    implementation(libs.commonsLang)
    implementation(libs.ant)
    implementation(libs.commonsCompress)

    runtimeOnly(projects.javaCompilerPlugin)

    testImplementation(projects.baseServicesGroovy)
    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.platformBase))
    testImplementation(testFixtures(projects.toolchainsJvm))

    testImplementation(libs.commonsIo)
    testImplementation(libs.nativePlatform) {
        because("Required for SystemInfo")
    }

    integTestImplementation(projects.messaging)
    // TODO: Make these available for all integration tests? Maybe all tests?
    integTestImplementation(libs.jetbrainsAnnotations)

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

tasks.withType<JavaCompile>().configureEach {
    options.release = null
    sourceCompatibility = "8"
    targetCompatibility = "8"
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

tasks.javadoc {
    // This project accesses JDK internals.
    // We would ideally add --add-exports flags for the required packages, however
    // due to limitations in the javadoc modeling API, we cannot specify multiple
    // flags for the same key.
    // Instead, we disable failure on javadoc errors.
    isFailOnError = false
    options {
        this as StandardJavadocDocletOptions
        addBooleanOption("quiet", true)
    }
}
