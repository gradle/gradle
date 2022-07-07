plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.update-init-template-versions")
}

description = """This project contains the Build Init plugin, which is automatically applied to the root project of every build, and provides the init and wrapper tasks.

This project should NOT be used as an implementation dependency anywhere (except when building a Gradle distribution)."""

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
    implementation(project(":wrapper-shared"))
    implementation(project(":testing-base"))

    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    implementation(libs.maven3SettingsBuilder)
    implementation(libs.maven3Model)
    implementation(libs.maven3Compat)
    implementation(libs.maven3PluginApi)
    implementation(libs.maven3Core)
    implementation(libs.maven3ResolverTransportHttp)
    implementation(libs.maven3ResolverConnectorBasic)

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
