plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:native")
    implementation("org.gradle:process-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")

    implementation("org.gradle:plugins")
    implementation("org.gradle:workers")
    implementation("org.gradle:reporting")

    implementation(libs.groovy)
    implementation(libs.groovyXml)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.ant)

    testImplementation("org.gradle:file-collections")
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:model-core"))

    testFixturesImplementation("org.gradle:core")
    testFixturesImplementation("org.gradle:core-api")
    testFixturesImplementation("org.gradle:base-services")

    testRuntimeOnly("org.gradle:distributions-core") {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly("org.gradle:distributions-full")
}

classycle {
    excludePatterns.add("org/gradle/api/plugins/quality/internal/*")
}
