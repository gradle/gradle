plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.update-init-template-versions")
}

description = """The Build Init plugin, which is automatically applied to the root project of every build, and provides 2 tasks: the init
task which allows for creating a new Gradle build, and the the wrapper task which allows for creating the Gradle wrapper files.

This project should NOT be used as an implementation dependency anywhere."""

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":file-collections"))
    implementation(project(":dependency-management"))
    implementation(project(":platform-base"))
    implementation(project(":platform-native"))
    implementation(project(":plugins"))
    implementation(project(":resources"))
    implementation(project(":workers"))
    implementation(project(":wrapper"))
    implementation(project(":testing-base"))

    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.maven3SettingsBuilder)

    compileOnly(libs.maven3Compat)
    compileOnly(libs.maven3PluginApi)
    testRuntimeOnly(libs.maven3Compat)
    testRuntimeOnly(libs.maven3PluginApi)

    testImplementation(project(":cli"))
    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":native"))
    testImplementation(project(":snapshots"))
    testImplementation(project(":process-services"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":platform-native")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":platform-base"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":plugins"))
    testFixturesImplementation(project(":testing-base"))

    integTestImplementation(project(":native"))
    integTestImplementation(libs.jetty)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
