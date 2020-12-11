plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:base-services-groovy")
    implementation("org.gradle:build-option")
    implementation("org.gradle:logging")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:reporting")
    implementation("org.gradle:snapshots")
    implementation(project(":platform-base"))
    implementation(project(":dependency-management"))

    implementation(libs.groovy)
    implementation(libs.groovyJson)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.jatl)

    testImplementation("org.gradle:process-services")
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:logging"))
    testImplementation(testFixtures(project(":dependency-management")))
    integTestImplementation(libs.jsoup)
    integTestImplementation(libs.jetty)

    testFixturesApi(testFixtures("org.gradle:platform-native"))
    testFixturesImplementation("org.gradle:base-services")
    testFixturesImplementation("org.gradle:internal-integ-testing")
    testFixturesImplementation(libs.guava)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly("org.gradle:distributions-full")  {
        because("There are integration tests that assert that all the tasks of a full distribution are reported (these should probably move to ':integTests').")
    }
}

classycle {
    excludePatterns.add("org/gradle/api/reporting/model/internal/*")
    excludePatterns.add("org/gradle/api/reporting/dependencies/internal/*")
    excludePatterns.add("org/gradle/api/plugins/internal/*")
}
