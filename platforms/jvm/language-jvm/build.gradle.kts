plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Contains some base and shared classes for JVM language support, like AbstractCompile class and BaseForkOptions classes,
JVM-specific dependencies blocks and JVM test suite interfaces."""

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":dependency-management"))
    implementation(project(":files"))
    implementation(project(":file-collections"))
    implementation(project(":logging"))
    implementation(project(":model-core"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":process-services"))
    implementation(project(":process-services"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":test-suites-base"))
    implementation(project(":toolchains-jvm"))
    implementation(project(":workers"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(project(":snapshots"))
    testImplementation(testFixtures(project(":core")))

    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(testFixtures(project(":resources-http")))

    testFixturesImplementation(libs.commonsLang)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-core")) {
        because("AbstractOptionsTest instantiates DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}
