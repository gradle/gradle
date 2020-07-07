plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":workerProcesses"))
    implementation(project(":fileCollections"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":jvmServices"))
    implementation(project(":workers"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":languageJvm"))
    implementation(project(":languageJava"))
    implementation(project(":files"))

    implementation(libs.slf4j_api)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.asm)
    implementation(libs.inject)

    testImplementation(project(":baseServicesGroovy"))
    testImplementation(project(":internalTesting"))
    testImplementation(project(":resources"))
    testImplementation(testFixtures(project(":core")))

    testFixturesApi(testFixtures(project(":languageJvm")))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":baseServices"))

    integTestImplementation(libs.commons_lang)

    testRuntimeOnly(project(":distributionsCore")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributionsCore"))
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/api/internal/tasks/compile/**",
        "org/gradle/api/tasks/javadoc/**"
    ))
}
