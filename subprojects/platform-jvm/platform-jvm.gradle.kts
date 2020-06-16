import org.gradle.gradlebuild.test.integrationtests.integrationTestUsesSampleDir


plugins {
    gradlebuild.distribution.`api-java`
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

    implementation(library("groovy"))
    implementation(library("guava"))
    implementation(library("commons_lang"))
    implementation(library("commons_io"))
    implementation(library("inject"))
    implementation(library("asm"))

    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(library("ant"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":platformBase")))
    testImplementation(testFixtures(project(":platformNative")))

    integTestImplementation(library("slf4j_api"))

    testRuntimeOnly(project(":distributionsCore")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}

strictCompile {
    ignoreDeprecations() // most of this project has been deprecated
}

integrationTestUsesSampleDir("subprojects/platform-jvm/src/main")
