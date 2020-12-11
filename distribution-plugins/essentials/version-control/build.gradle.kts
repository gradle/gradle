plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:messaging")
    implementation("org.gradle:logging")
    implementation("org.gradle:files")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:persistent-cache")
    implementation("org.gradle:core-api")
    implementation("org.gradle:core")
    implementation("org.gradle:resources")
    implementation(project(":dependency-management"))

    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.jgit)
    implementation(libs.jsch)

    testImplementation("org.gradle:native")
    testImplementation("org.gradle:snapshots")
    testImplementation("org.gradle:process-services")
    testImplementation(testFixtures("org.gradle:core"))

    testFixturesImplementation("org.gradle:base-services")
    testFixturesImplementation("org.gradle:internal-integ-testing")

    testFixturesImplementation(libs.jgit)
    testFixturesImplementation(libs.commonsIo)
    testFixturesImplementation(libs.commonsHttpclient)
    testFixturesImplementation(libs.jsch)
    testFixturesImplementation(libs.guava)

    integTestImplementation("org.gradle:launcher")
    integTestDistributionRuntimeOnly("org.gradle:distributions-basics")
}
