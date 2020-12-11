plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:execution")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")

    implementation("org.gradle:dependency-management")
    implementation("org.gradle:plugins")
    implementation("org.gradle:platform-jvm")

    implementation(libs.groovy)
    implementation(libs.groovyXml)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

    testImplementation("org.gradle:native")
    testImplementation("org.gradle:base-services-groovy")
    testImplementation(libs.ant)
    testImplementation(testFixtures("org.gradle:core"))

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
