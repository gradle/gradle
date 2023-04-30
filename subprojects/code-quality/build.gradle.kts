plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins and integration with code quality (Checkstyle, PMD, CodeNarc)"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":native"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":plugins"))
    implementation(project(":workers"))
    implementation(project(":reporting"))
    implementation(project(":platform-jvm"))
    implementation(project(":file-collections"))
    compileOnly(project(":internal-instrumentation-api"))

    implementation(libs.groovy)
    implementation(libs.groovyAnt)
    implementation(libs.groovyXml)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.ant)

    testImplementation(project(":file-collections"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))

    testFixturesImplementation(project(":core"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":base-services"))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-full"))

    integTestImplementation(testFixtures(project(":language-groovy")))
}

packageCycles {
    excludePatterns.add("org/gradle/api/plugins/quality/internal/*")
}
