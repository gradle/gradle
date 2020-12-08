plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":file-collections"))
    implementation(project(":execution"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":plugins"))
    implementation(project(":platform-jvm"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation(project(":native"))
    testImplementation(project(":base-services-groovy"))
    testImplementation(libs.ant)
    testImplementation(testFixtures(project(":core")))

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

classycle {
    excludePatterns.add("org/gradle/plugins/ear/internal/*")
}
