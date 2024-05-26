plugins {
    id("gradlebuild.distribution.api-java")
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
        "UnusedMethod", // 4 occurrences
        "UnusedVariable", // 1 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serialization)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":build-events"))
    api(project(":build-operations"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":file-collections"))
    api(project(":files"))
    api(project(":hashing"))
    api(project(":language-jvm"))
    api(project(":persistent-cache"))
    api(project(":platform-base"))
    api(project(":platform-jvm"))
    api(project(":problems-api"))
    api(project(":process-services"))
    api(project(":snapshots"))
    api(project(":test-suites-base"))
    api(project(":toolchains-jvm"))
    api(project(":toolchains-jvm-shared"))
    api(project(":worker-main"))
    api(project(":workers"))
    api(project(":build-process-services"))

    api(libs.asm)
    api(libs.fastutil)
    api(libs.groovy)
    api(libs.guava)
    api(libs.jsr305)
    api(libs.inject)

    implementation(projects.concurrent)
    implementation(projects.time)
    implementation(project(":file-temp"))
    implementation(project(":logging-api"))
    implementation(project(":model-core"))
    implementation(project(":tooling-api"))

    api(libs.slf4jApi)
    implementation(libs.commonsLang)
    implementation(libs.ant)
    implementation(libs.commonsCompress)

    runtimeOnly(project(":java-compiler-plugin"))

    testImplementation(project(":base-services-groovy"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":toolchains-jvm")))

    testImplementation(libs.commonsIo)
    testImplementation(libs.nativePlatform) {
        because("Required for SystemInfo")
    }

    integTestImplementation(projects.messaging)
    // TODO: Make these available for all integration tests? Maybe all tests?
    integTestImplementation(libs.jetbrainsAnnotations)

    testFixturesApi(testFixtures(project(":language-jvm")))
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":enterprise-operations"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":model-core"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":platform-base"))
    testFixturesImplementation(project(":persistent-cache"))
    testFixturesImplementation(libs.slf4jApi)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder test (JavaLanguagePluginTest) loads services from a Gradle distribution.")
    }

    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-basics"))
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

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
