plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":workerProcesses"))
    implementation(project(":fileCollections"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":jvm-services"))
    implementation(project(":workers"))
    implementation(project(":platformBase"))
    implementation(project(":platformJvm"))
    implementation(project(":languageJvm"))
    implementation(project(":languageJava"))
    implementation(project(":files"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.asm)
    implementation(libs.inject)

    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":internalTesting"))
    testImplementation(project(":resources"))
    testImplementation(testFixtures(project(":core")))

    testFixturesApi(testFixtures(project(":languageJvm")))
    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":base-services"))

    integTestImplementation(libs.commonsLang)

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

classycle {
    excludePatterns.set(listOf(
        "org/gradle/api/internal/tasks/compile/**",
        "org/gradle/api/tasks/javadoc/**"
    ))
}
