plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-project")
}

description = """Contains some base and shared classes for JVM language support, like AbstractCompile class and BaseForkOptions classes,
JVM-specific dependencies blocks and JVM test suite interfaces."""

errorprone {
    disabledChecks.addAll(
        "OverridesJavaxInjectableMethod", // 1 occurrences
        "UnusedMethod", // 1 occurrences
        "UnusedVariable", // 1 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":files"))
    api(project(":platform-base"))
    api(project(":platform-jvm"))
    api(project(":process-services"))
    api(project(":workers"))

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(project(":dependency-management"))
    implementation(project(":logging"))
    implementation(project(":model-core"))
    implementation(project(":test-suites-base"))

    implementation(libs.guava)

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
