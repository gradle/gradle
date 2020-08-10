import gradlebuild.cleanup.WhenNotEmpty

plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.launchable-jar")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":cli"))
    implementation(project(":messaging"))
    implementation(project(":buildOption"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":files"))
    implementation(project(":fileCollections"))
    implementation(project(":snapshots"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":bootstrap"))
    implementation(project(":jvm-services"))
    implementation(project(":build-events"))
    implementation(project(":tooling-api"))
    implementation(project(":fileWatching"))

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
    manifestClasspath(project(":coreApi"))
    manifestClasspath(project(":core"))
    manifestClasspath(project(":persistentCache"))

    testImplementation(project(":internalIntegTesting"))
    testImplementation(project(":native"))
    testImplementation(project(":cli"))
    testImplementation(project(":process-services"))
    testImplementation(project(":coreApi"))
    testImplementation(project(":modelCore"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":base-services-groovy")) // for 'Specs'

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":languageJava")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":tooling-api")))

    integTestImplementation(project(":persistentCache"))
    integTestImplementation(libs.slf4jApi)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.commonsLang)
    integTestImplementation(libs.commonsIo)

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-native")) {
        because("'native' distribution requried for 'ProcessCrashHandlingIntegrationTest.session id of daemon is different from daemon client'")
    }
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

// Needed for testing debug command line option (JDWPUtil) - 'CommandLineIntegrationSpec.can debug with org.gradle.debug=true'
val toolsJar = buildJvms.testJvm.map { jvm -> jvm.jdk.get().toolsClasspath }
dependencies {
    integTestRuntimeOnly(toolsJar)
}
