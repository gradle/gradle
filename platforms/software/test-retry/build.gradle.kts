plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.distribution.api-kotlin")
}

description = "Bundled Gradle plugin that mitigates flaky tests by retrying them when they fail"

sourceSets.main {
    // All Java sources in this subproject are copied verbatim from the upstream
    // test-retry-gradle-plugin and would trigger multiple errorprone checks.
    // Disable errorprone for the main sourceset rather than modify any imported file.
    errorprone.enabled = false
}

dependencies {
    api(projects.coreApi)

    compileOnly(projects.kotlinDsl)

    implementation(projects.baseServices)
    implementation(projects.core)
    implementation(projects.messaging)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.testingBase)
    implementation(projects.testingJvm)

    implementation(libs.asm)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.jspecify)

    testImplementation(libs.groovy)
    testImplementation(testLibs.spock)
    testImplementation(gradleTestKit())
}
