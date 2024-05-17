plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugin for cryptographic signing of publications, artifacts or files."

dependencies {
    api(projects.javaLanguageExtensions)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":file-collections"))
    api(project(":publish"))
    api(project(":security"))

    api(libs.jsr305)
    api(libs.groovy)
    api(libs.inject)

    implementation(project(":model-core"))
    implementation(project(":functional"))
    implementation(project(":platform-base"))

    implementation(libs.guava)

    testFixturesImplementation(project(":base-services")) {
        because("Required to access org.gradle.internal.SystemProperties")
    }

    testImplementation(project(":maven"))
    testImplementation(project(":ivy"))
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(testFixtures(project(":security")))
    testRuntimeOnly(project(":distributions-publishing")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }

    integTestDistributionRuntimeOnly(project(":distributions-publishing"))
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
