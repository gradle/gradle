import gradlebuild.integrationtests.integrationTestUsesSampleDir

plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":fileCollections"))
    implementation(project(":execution"))
    implementation(project(":process-services"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":base-services-groovy"))
    implementation(project(":dependency-management"))
    implementation(project(":platformBase"))
    implementation(project(":diagnostics"))
    implementation(project(":normalizationJava"))
    implementation(project(":resources"))

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
    testImplementation(testFixtures(project(":platformBase")))
    testImplementation(testFixtures(project(":platformNative")))

    integTestImplementation(libs.slf4jApi)

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

strictCompile {
    ignoreDeprecations() // most of this project has been deprecated
}

integrationTestUsesSampleDir("subprojects/platform-jvm/src/main")
