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
    implementation(project(":language-jvm"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":platform-native"))
    implementation(project(":plugins")) {
        because("Needs access to StartScriptGenerator.")
    }
    implementation(project(":plugins-java"))
    implementation(project(":resources"))
    implementation(project(":workers"))
    implementation(project(":wrapper"))
    implementation(project(":wrapper-shared"))
    implementation(project(":testing-base"))
    implementation(project(":toolchains-jvm"))
    implementation(project(":plugins-jvm-test-suite"))

    implementation(libs.groovy)
    implementation(libs.groovyTemplates)
    implementation(libs.guava)
    implementation(libs.gson)
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
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(project(":plugins"))
    testFixturesImplementation(project(":plugins-java"))
    testFixturesImplementation(project(":testing-base"))
    testFixturesImplementation(project(":test-suites-base"))
    testFixturesImplementation(project(":plugins-jvm-test-suite"))

    integTestImplementation(project(":native"))
    integTestImplementation(libs.jetty)

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.  Toolchain usage requires JVM distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

packageCycles {
    excludePatterns.add("org/gradle/api/tasks/wrapper/internal/*")
}
