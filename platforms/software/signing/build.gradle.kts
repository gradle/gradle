plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugin for cryptographic signing of publications, artifacts or files."

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":file-collections"))
    implementation(project(":functional"))
    implementation(project(":platform-base"))
    implementation(project(":dependency-management"))
    implementation(project(":publish"))
    implementation(project(":maven"))
    implementation(project(":security"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)

    testFixturesImplementation(project(":base-services")) {
        because("Required to access org.gradle.internal.SystemProperties")
    }

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
