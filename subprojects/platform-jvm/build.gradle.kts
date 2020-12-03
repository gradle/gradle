plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":file-collections"))
    implementation(project(":execution"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":base-services-groovy"))
    implementation(project(":dependency-management"))
    implementation(project(":platform-base"))
    implementation(project(":diagnostics"))
    implementation(project(":normalization-java"))
    implementation(project(":resources"))
    implementation(project(":jvm-services"))
    implementation(project(":persistent-cache"))
    implementation(project(":native"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsCompress)
    implementation(libs.commonsIo)
    implementation(libs.inject)
    implementation(libs.asm)
    implementation(libs.nativePlatform)

    testImplementation(project(":snapshots"))
    testImplementation(libs.ant)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":platform-native")))

    integTestImplementation(libs.slf4jApi)

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

strictCompile {
    ignoreDeprecations() // most of this project has been deprecated
}

classycle {
    excludePatterns.set(listOf(
        // Needed for the factory methods in the interface
        "org/gradle/jvm/toolchain/JavaLanguageVersion**",
        "org/gradle/jvm/toolchain/internal/**"
    ))
}

integTest.usesSamples.set(true)
