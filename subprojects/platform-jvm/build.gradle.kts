plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.distribution.api-kotlin")
}

description = """Extends platform-base with base types and interfaces specific to the Java Virtual Machine, including tasks for obtaining a JDK via toolchains, and for compiling and launching Java applications."""

dependencies {
    implementation(project(":base-services"))
    implementation(project(":base-services-groovy"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":functional"))
    implementation(project(":dependency-management"))
    implementation(project(":diagnostics"))
    implementation(project(":execution"))
    implementation(project(":file-collections"))
    implementation(project(":jvm-services"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":model-core"))
    implementation(project(":native"))
    implementation(project(":normalization-java"))
    implementation(project(":persistent-cache"))
    implementation(project(":platform-base"))
    implementation(project(":process-services"))
    implementation(project(":resources"))
    implementation(project(":enterprise-operations"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.inject)
    implementation(libs.nativePlatform)
    implementation(libs.futureKotlin("stdlib-jdk8"))

    testImplementation(project(":snapshots"))
    testImplementation(libs.ant)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":platform-native")))
    testImplementation(testFixtures(project(":plugins")))

    integTestImplementation(libs.slf4jApi)

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

strictCompile {
    ignoreDeprecations() // most of this project has been deprecated
}

packageCycles {
    // Needed for the factory methods in the interface
    excludePatterns.add("org/gradle/jvm/toolchain/JavaLanguageVersion**")
    excludePatterns.add("org/gradle/jvm/toolchain/internal/**")
    excludePatterns.add("org/gradle/api/plugins/internal/**")
}

integTest.usesJavadocCodeSnippets = true
