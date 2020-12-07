plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":file-collections"))
    implementation(project(":plugins"))
    implementation(project(":dependency-management"))
    implementation(project(":publish"))
    implementation(project(":maven"))
    implementation(project(":security"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.inject)

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

classycle {
    excludePatterns.add("org/gradle/plugins/signing/**")
}

integTest.usesSamples.set(true)
