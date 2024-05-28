plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins, tasks and compiler infrastructure for compiling/linking code"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 2 occurrences
        "EqualsUnsafeCast", // 1 occurrences
        "GetClassOnClass", // 1 occurrences
        "HidingField", // 1 occurrences
        "ImmutableEnumChecker", // 2 occurrences
        "ReferenceEquality", // 2 occurrences
        "StaticAssignmentInConstructor", // 1 occurrences
        "StringCaseLocaleUsage", // 3 occurrences
        "StringCharset", // 2 occurrences
        "UnnecessaryTypeArgument", // 2 occurrences
        "UnusedMethod", // 11 occurrences
        "UnusedTypeParameter", // 1 occurrences
        "UnusedVariable", // 6 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":diagnostics"))
    api(project(":file-collections"))
    api(project(":files"))
    api(project(":hashing"))
    api(project(":java-language-extensions"))
    api(project(":logging"))
    api(project(":model-core"))
    api(project(":native"))
    api(project(":platform-base"))
    api(project(":workers"))

    api(libs.jsr305)
    api(libs.inject)
    api(libs.nativePlatform)
    api(libs.slf4jApi)

    implementation(project(":enterprise-logging"))
    implementation(projects.io)
    implementation(project(":logging-api"))
    implementation(project(":process-services"))

    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.gson)
    implementation(libs.guava)
    implementation(libs.snakeyaml)

    testFixturesApi(project(":resources"))
    testFixturesApi(testFixtures(project(":ide")))
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":native"))
    testFixturesImplementation(project(":platform-base"))
    testFixturesImplementation(project(":file-collections"))
    testFixturesImplementation(project(":process-services"))
    testFixturesImplementation(project(":snapshots"))
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(libs.nativePlatform)
    testFixturesImplementation(libs.groovyXml)
    testFixturesImplementation(libs.commonsLang)
    testFixturesImplementation(libs.commonsIo)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":model-core")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":snapshots")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-native")) {
        because("Required 'ideNative' to test visual studio project file generation for generated sources")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/nativeplatform/plugins/**")
    excludePatterns.add("org/gradle/nativeplatform/tasks/**")
    excludePatterns.add("org/gradle/nativeplatform/internal/resolve/**")
    excludePatterns.add("org/gradle/nativeplatform/toolchain/internal/**")
}

integTest.usesJavadocCodeSnippets = true
