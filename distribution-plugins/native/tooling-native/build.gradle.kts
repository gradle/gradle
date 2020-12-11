plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:tooling-api")

    implementation("org.gradle:platform-base")
    implementation("org.gradle:ide") {
        because("To pick up various builders (which should live somewhere else)")
    }

    implementation(project(":platform-native"))
    implementation(project(":language-native"))
    implementation(project(":testing-native"))

    implementation(libs.guava)

    testImplementation(testFixtures(project(":platform-native")))

    crossVersionTestDistributionRuntimeOnly(project(":distributions-native"))
}
