import gradlebuild.cleanup.WhenNotEmpty
import gradlebuild.integrationtests.integrationTestUsesSampleDir

plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":workerProcesses"))
    implementation(project(":files"))
    implementation(project(":fileCollections"))
    implementation(project(":persistentCache"))
    implementation(project(":jvm-services"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":snapshots"))
    implementation(project(":execution"))
    implementation(project(":dependency-management"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":languageJvm"))
    implementation(project(":build-events"))
    implementation(project(":tooling-api"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.fastutil)
    implementation(libs.ant) // for 'ZipFile' and 'ZipEntry'
    implementation(libs.asm)
    implementation(libs.asmCommons)
    implementation(libs.inject)

    runtimeOnly(project(":java-compiler-plugin"))

    testImplementation(project(":base-services-groovy"))
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platformBase")))

    testFixturesApi(testFixtures(project(":languageJvm")))
    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":coreApi"))
    testFixturesImplementation(project(":modelCore"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(project(":platformBase"))
    testFixturesImplementation(project(":persistentCache"))
    testFixturesImplementation(libs.slf4jApi)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder test (JavaLanguagePluginTest) loads services from a Gradle distribution.")
    }

    integTestDistributionRuntimeOnly(project(":distributions-core"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-basics"))

    buildJvms.whenTestingWithEarlierThan(JavaVersion.VERSION_1_9) {
        val tools = it.jdk.get().toolsClasspath
        testRuntimeOnly(tools)
    }
}

strictCompile {
    ignoreDeprecations() // this project currently uses many deprecated part from 'platform-jvm'
}

classycle {
    // These public packages have classes that are tangled with the corresponding internal package.
    excludePatterns.set(listOf(
        "org/gradle/api/tasks/compile/**",
        "org/gradle/external/javadoc/**"
    ))
}

testFilesCleanup {
    policy.set(WhenNotEmpty.REPORT)
}

integrationTestUsesSampleDir("subprojects/language-java/src/main")
