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
    api(project(":base-annotations"))
    api(project(":build-operations"))
    api(project(":hashing"))
    api(project(":problems-api"))

    api(libs.asm)
    api(libs.fastutil)
    api(libs.groovy)
    api(libs.guava)
    api(libs.jsr305)
    api(libs.inject)

    implementation(project(":base-services"))
    implementation(project(":build-events"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":dependency-management"))
    implementation(project(":files"))
    implementation(project(":file-collections"))
    implementation(project(":file-temp"))
    implementation(project(":language-jvm"))
    implementation(project(":logging-api"))
    implementation(project(":messaging"))
    implementation(project(":model-core"))
    implementation(project(":persistent-cache"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":process-services"))
    implementation(project(":snapshots"))
    implementation(project(":test-suites-base"))
    implementation(project(":toolchains-jvm"))
    implementation(project(":tooling-api"))
    implementation(project(":workers"))
    implementation(project(":worker-processes"))

    implementation(libs.slf4jApi)
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

    integTestDistributionRuntimeOnly(project(":distributions-core"))
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
