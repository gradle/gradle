import gradlebuild.integrationtests.integrationTestUsesSampleDir

plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":fileCollections"))
    implementation(project(":execution"))
    implementation(project(":processServices"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":baseServicesGroovy"))
    implementation(project(":dependencyManagement"))
    implementation(project(":platformBase"))
    implementation(project(":diagnostics"))
    implementation(project(":normalizationJava"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.inject)
    implementation(libs.asm)

    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(libs.ant)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":platformBase")))
    testImplementation(testFixtures(project(":platformNative")))

    integTestImplementation(libs.slf4jApi)

    testRuntimeOnly(project(":distributionsCore")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}

strictCompile {
    ignoreDeprecations() // most of this project has been deprecated
}

integrationTestUsesSampleDir("subprojects/platform-jvm/src/main")
