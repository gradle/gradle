plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:messaging")
    implementation("org.gradle:logging")
    implementation("org.gradle:process-services")
    implementation("org.gradle:worker-processes")
    implementation("org.gradle:persistent-cache")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:snapshots")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:files")
    implementation("org.gradle:native")
    implementation("org.gradle:resources")

    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation("org.gradle:native")
    testImplementation("org.gradle:file-collections")
    testImplementation("org.gradle:resources")
    testImplementation("org.gradle:snapshots")
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:logging"))

    integTestRuntimeOnly("org.gradle:kotlin-dsl")
    integTestRuntimeOnly("org.gradle:api-metadata")
    integTestRuntimeOnly(project(":kotlin-dsl-provider-plugins"))
    integTestRuntimeOnly("org.gradle:test-kit")

    integTestImplementation("org.gradle:jvm-services")

    testFixturesImplementation(libs.inject)
    testFixturesImplementation(libs.groovyJson)
    testFixturesImplementation("org.gradle:base-services")

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
