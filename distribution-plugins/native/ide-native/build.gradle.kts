plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:file-collections")

    implementation("org.gradle:dependency-management")
    implementation("org.gradle:ide")
    implementation("org.gradle:platform-base")
    implementation("org.gradle:testing-base")

    implementation(project(":platform-native"))
    implementation(project(":language-native"))
    implementation(project(":testing-native"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.plist)

    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:version-control"))
    testImplementation(testFixtures(project(":platform-native")))
    testImplementation(testFixtures(project(":language-native")))

    integTestImplementation("org.gradle:native")
    integTestImplementation(libs.commonsIo)
    integTestImplementation(libs.jgit)

    testFixturesApi(testFixtures("org.gradle:ide"))
    testFixturesImplementation(libs.plist)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(libs.groovyXml)
    testFixturesImplementation(testFixtures("org.gradle:ide"))

    testRuntimeOnly("org.gradle:distributions-core") {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-native"))
}

integTest.usesSamples.set(true)
