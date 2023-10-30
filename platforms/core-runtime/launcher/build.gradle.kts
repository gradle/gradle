plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.launchable-jar")
}

description = "Implementation for launching, controlling and communicating with Gradle Daemon from CLI and TAPI"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":functional"))
    implementation(project(":enterprise-operations"))
    implementation(project(":cli"))
    implementation(project(":messaging"))
    implementation(project(":build-option"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":files"))
    implementation(project(":file-collections"))
    implementation(project(":snapshots"))
    implementation(project(":execution"))
    implementation(project(":persistent-cache"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":model-core"))
    implementation(project(":bootstrap"))
    implementation(project(":jvm-services"))
    implementation(project(":build-events"))
    implementation(project(":tooling-api"))
    implementation(project(":file-watching"))
    implementation(project(":problems-api"))

    implementation(libs.groovy) // for 'ReleaseInfo.getVersion()'
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.asm)
    implementation(libs.ant)

    runtimeOnly(libs.asm)
    runtimeOnly(libs.commonsIo)
    runtimeOnly(libs.commonsLang)
    runtimeOnly(libs.slf4jApi)

    manifestClasspath(project(":bootstrap"))
    manifestClasspath(project(":base-services"))
    manifestClasspath(project(":worker-services"))
    manifestClasspath(project(":core-api"))
    manifestClasspath(project(":core"))
    manifestClasspath(project(":persistent-cache"))

    agentsClasspath(project(":instrumentation-agent"))

    testImplementation(project(":internal-integ-testing"))
    testImplementation(project(":native"))
    testImplementation(project(":cli"))
    testImplementation(project(":process-services"))
    testImplementation(project(":core-api"))
    testImplementation(project(":model-core"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":base-services-groovy")) // for 'Specs'

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":language-java")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":tooling-api")))

    integTestImplementation(project(":persistent-cache"))
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.commonsLang)
    integTestImplementation(libs.commonsIo)

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full")) {
        because("built-in options are required to be present at runtime for 'TaskOptionsSpec'")
    }
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

testFilesCleanup.reportOnly = true

