plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Plugin for cryptographic signing of publications, artifacts or files."

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.fileCollections)
    api(projects.publish)
    api(projects.security)

    api(libs.jsr305)
    api(libs.groovy)
    api(libs.inject)

    implementation(projects.modelCore)
    implementation(projects.functional)
    implementation(projects.platformBase)

    implementation(libs.guava)

    testFixturesImplementation(projects.baseServices) {
        because("Required to access org.gradle.internal.SystemProperties")
    }

    testImplementation(projects.maven)
    testImplementation(projects.ivy)
    testImplementation(testFixtures(projects.core))

    testRuntimeOnly(testFixtures(projects.security))
    testRuntimeOnly(projects.distributionsPublishing) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    integTestDistributionRuntimeOnly(projects.distributionsPublishing)
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

packageCycles {
    excludePatterns.add("org/gradle/plugins/signing/**")
}

tasks {
    integTest {
        usesJavadocCodeSnippets = true
    }
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
