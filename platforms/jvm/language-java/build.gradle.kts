plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Source for JavaCompile, JavaExec and Javadoc tasks, it also contains logic for incremental Java compilation"

gradlebuildJava {
    usesJdkInternals = true
}

errorprone {
    disabledChecks.addAll(
        "CheckReturnValue", // 2 occurrences
        "DoNotClaimAnnotations", // 6 occurrences
        "InconsistentCapitalization", // 1 occurrences
        "InvalidInlineTag", // 3 occurrences
        "MissingCasesInEnumSwitch", // 1 occurrences
        "MixedMutabilityReturnType", // 3 occurrences
    )
}

dependencies {
    api(projects.baseServices)
    api(projects.buildEvents)
    api(projects.buildOperations)
    api(projects.buildProcessServices)
    api(projects.classloaders)
    api(projects.core)
    api(projects.coreApi)
    api(projects.daemonServerWorker)
    api(projects.dependencyManagement)
    api(projects.fileCollections)
    api(projects.fileOperations)
    api(projects.files)
    api(projects.hashing)
    api(projects.jvmServices)
    api(projects.languageJvm)
    api(projects.modelCore)
    api(projects.persistentCache)
    api(projects.platformBase)
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
    api(libs.guava)
    api(libs.jspecify)
    api(libs.inject)

    implementation(projects.concurrent)
    implementation(projects.serviceLookup)
    implementation(projects.time)
    implementation(projects.fileTemp)
    implementation(projects.logging)
    implementation(projects.loggingApi)
    implementation(projects.logging)
    implementation(projects.problemsRendering)
    implementation(projects.toolingApi)

    api(libs.slf4jApi)
    implementation(libs.commonsLang)
    implementation(libs.ant)
    implementation(libs.commonsCompress)

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

tasks.javadoc {
    options {
        this as StandardJavadocDocletOptions
        // This project accesses JDK internals, which we need to open up so that javadoc can access them
        addMultilineStringsOption("-add-exports").value = listOf(
            "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED"
        )
    }
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
