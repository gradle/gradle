plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:execution")
    implementation("org.gradle:process-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:base-services-groovy")
    implementation("org.gradle:normalization-java")
    implementation("org.gradle:resources")
    implementation("org.gradle:jvm-services")
    implementation("org.gradle:persistent-cache")
    implementation("org.gradle:native")
    implementation(project(":dependency-management"))
    implementation(project(":platform-base"))
    implementation(project(":diagnostics"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.inject)
    implementation(libs.nativePlatform)

    testImplementation("org.gradle:snapshots")
    testImplementation(libs.ant)
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:diagnostics"))
    testImplementation(testFixtures("org.gradle:logging"))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures("org.gradle:platform-native"))

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
    // Needed for the factory methods in the interface
    excludePatterns.add("org/gradle/jvm/toolchain/JavaLanguageVersion**")
    excludePatterns.add("org/gradle/jvm/toolchain/internal/**")
}

integTest.usesSamples.set(true)
