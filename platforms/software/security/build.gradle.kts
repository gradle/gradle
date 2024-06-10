plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Shared classes for projects requiring GPG support"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
    )
}

dependencies {
    api(project(":core-api"))
    api(project(":resources"))

    api(libs.bouncycastlePgp)
    api(libs.jsr305)

    implementation(projects.javaLanguageExtensions)
    implementation(projects.time)
    implementation(project(":base-services"))
    implementation(project(":functional"))
    implementation(project(":logging-api"))
    implementation(project(":process-services"))

    implementation(libs.bouncycastleProvider)
    implementation(libs.guava)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.jetty)
    testFixturesImplementation(libs.jettyWebApp)
    testFixturesImplementation(testFixtures(project(":core")))
    testFixturesImplementation(project(":internal-integ-testing"))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
}

packageCycles {
    excludePatterns.add("org/gradle/plugins/signing/type/pgp/**")
}
