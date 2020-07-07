import gradlebuild.cleanup.WhenNotEmpty
import gradlebuild.integrationtests.integrationTestUsesSampleDir

plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":workerProcesses"))
    implementation(project(":files"))
    implementation(project(":fileCollections"))
    implementation(project(":persistentCache"))
    implementation(project(":jvmServices"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":workers"))
    implementation(project(":snapshots"))
    implementation(project(":execution"))
    implementation(project(":dependencyManagement"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":languageJvm"))
    implementation(project(":buildEvents"))
    implementation(project(":toolingApi"))

    implementation(libs.groovy)
    implementation(libs.slf4j_api)
    implementation(libs.guava)
    implementation(libs.commons_lang)
    implementation(libs.fastutil)
    implementation(libs.ant) // for 'ZipFile' and 'ZipEntry'
    implementation(libs.asm)
    implementation(libs.asm_commons)
    implementation(libs.inject)

    runtimeOnly(project(":javaCompilerPlugin"))

    testImplementation(project(":baseServicesGroovy"))
    testImplementation(libs.commons_io)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platformBase")))

    testFixturesApi(testFixtures(project(":languageJvm")))
    testFixturesImplementation(project(":baseServices"))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":coreApi"))
    testFixturesImplementation(project(":modelCore"))
    testFixturesImplementation(project(":internalIntegTesting"))
    testFixturesImplementation(project(":platformBase"))
    testFixturesImplementation(project(":persistentCache"))
    testFixturesImplementation(libs.slf4j_api)

    testRuntimeOnly(project(":distributionsCore")) {
        because("ProjectBuilder test (JavaLanguagePluginTest) loads services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributionsCore"))
    crossVersionTestDistributionRuntimeOnly(project(":distributionsBasics"))
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
