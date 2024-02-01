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
        "StringSplitter", // 1 occurrences
        "UnnecessaryParentheses", // 1 occurrences
        "UnnecessaryTypeArgument", // 2 occurrences
        "UnusedMethod", // 11 occurrences
        "UnusedTypeParameter", // 1 occurrences
        "UnusedVariable", // 6 occurrences
    )
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":native"))
    implementation(project(":process-services"))
    implementation(project(":file-collections"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":platform-base"))
    implementation(project(":diagnostics"))

    implementation(libs.nativePlatform)
    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.snakeyaml)
    implementation(libs.gson)
    implementation(libs.inject)

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
