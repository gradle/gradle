plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.launchable-jar")
}

description = "Implementation for launching, controlling and communicating with Gradle Daemon from CLI and TAPI"

dependencies {
    api(project(":base-services"))
    api(project(":build-events"))
    api(project(":build-operations"))
    api(project(":build-option"))
    api(project(":build-state"))
    api(project(":cli"))
    api(project(":concurrent"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":daemon-protocol"))
    api(project(":enterprise-logging"))
    api(project(":execution"))
    api(project(":file-collections"))
    api(project(":file-watching"))
    api(project(":files"))
    api(project(":hashing"))
    api(project(":java-language-extensions"))
    api(project(":jvm-services"))
    api(project(":logging"))
    api(project(":logging-api"))
    api(project(":messaging"))
    api(project(":model-core"))
    api(project(":native"))
    api(project(":persistent-cache"))
    api(project(":process-services"))
    api(project(":serialization"))
    api(project(":service-provider"))
    api(project(":snapshots"))
    api(project(":time"))
    api(project(":toolchains-jvm-shared"))
    api(project(":tooling-api"))

    // This project contains the Gradle client, daemon and tooling API provider implementations.
    // It should be split up, but for now, add dependencies on both the client and daemon pieces
    api(project(":client-services"))
    api(project(":daemon-services"))

    api(libs.guava)
    api(libs.jsr305)

    implementation(project(":build-configuration"))
    implementation(project(":enterprise-operations"))
    implementation(project(":functional"))
    implementation(projects.io)
    implementation(project(":problems-api"))
    implementation(project(":build-process-services"))

    implementation(libs.groovy) // for 'ReleaseInfo.getVersion()'
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.ant)

    runtimeOnly(project(":gradle-cli-main"))
    runtimeOnly(project(":declarative-dsl-provider"))
    runtimeOnly(project(":problems"))

    runtimeOnly(libs.commonsIo)
    runtimeOnly(libs.commonsLang)
    runtimeOnly(libs.slf4jApi)

    // The wrapper expects the launcher Jar to have classpath entries that contain the main class and its runtime classpath
    manifestClasspath(project(":gradle-cli-main"))

    testImplementation(project(":internal-integ-testing"))
    testImplementation(project(":native"))
    testImplementation(project(":cli"))
    testImplementation(project(":process-services"))
    testImplementation(project(":core-api"))
    testImplementation(project(":model-core"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":base-services-groovy")) // for 'Specs'

    testImplementation(testFixtures(projects.serialization))
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
    integTestImplementation(testFixtures(project(":build-configuration")))

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
