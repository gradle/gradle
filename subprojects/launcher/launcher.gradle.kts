import gradlebuild.cleanup.WhenNotEmpty

plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.launchable-jar")
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":cli"))
    implementation(project(":messaging"))
    implementation(project(":buildOption"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":files"))
    implementation(project(":fileCollections"))
    implementation(project(":snapshots"))
    implementation(project(":persistentCache"))
    implementation(project(":coreApi"))
    implementation(project(":core"))
    implementation(project(":bootstrap"))
    implementation(project(":jvmServices"))
    implementation(project(":buildEvents"))
    implementation(project(":toolingApi"))
    implementation(project(":fileWatching"))

    implementation(libs.groovy) // for 'ReleaseInfo.getVersion()'
    implementation(libs.slf4j_api)
    implementation(libs.guava)
    implementation(libs.commons_io)
    implementation(libs.commons_lang)
    implementation(libs.asm)
    implementation(libs.ant)

    runtimeOnly(libs.asm)
    runtimeOnly(libs.commons_io)
    runtimeOnly(libs.commons_lang)
    runtimeOnly(libs.slf4j_api)

    manifestClasspath(project(":bootstrap"))
    manifestClasspath(project(":baseServices"))
    manifestClasspath(project(":coreApi"))
    manifestClasspath(project(":core"))
    manifestClasspath(project(":persistentCache"))

    testImplementation(project(":internalIntegTesting"))
    testImplementation(project(":native"))
    testImplementation(project(":cli"))
    testImplementation(project(":processServices"))
    testImplementation(project(":coreApi"))
    testImplementation(project(":modelCore"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":baseServicesGroovy")) // for 'Specs'

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":languageJava")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":toolingApi")))

    integTestImplementation(project(":persistentCache"))
    integTestImplementation(libs.slf4j_api)
    integTestImplementation(libs.guava)
    integTestImplementation(libs.commons_lang)
    integTestImplementation(libs.commons_io)

    testRuntimeOnly(project(":distributionsCore")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributionsNative")) {
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
