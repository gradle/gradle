plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Contains the tooling for Javadoc documentation generation."""

dependencies {
    api(projects.baseCompilerWorker)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.files)
    api(projects.jvmServices)
    api(projects.modelCore)
    api(projects.serviceProvider)
    api(projects.toolchainsJvm)
    api(projects.toolchainsJvmShared)

    api(libs.groovy)
    api(libs.jspecify)
    api(libs.inject)

    implementation(projects.fileCollections)
    implementation(projects.javaCompilerWorker)

    implementation(libs.guava)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.core))

    testRuntimeOnly(projects.distributionsCore) {
        because("JavadocTest loads services from a Gradle distribution.")
    }

    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

packageCycles {
    // These public packages have classes that are tangled with the corresponding internal package.
    excludePatterns.add("org/gradle/external/javadoc/**")
    excludePatterns.add("org/gradle/api/tasks/javadoc/**")
}
