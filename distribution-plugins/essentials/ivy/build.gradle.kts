plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:base-services-groovy") // for 'Specs'
    implementation("org.gradle:file-collections")
    implementation("org.gradle:resources")
    implementation(project(":publish"))
    implementation(project(":plugins")) // for base plugin to get archives conf
    implementation(project(":plugin-use"))
    implementation(project(":dependency-management"))

    implementation(libs.groovy) // for 'Closure' and 'Task.property(String propertyName) throws groovy.lang.MissingPropertyException'
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)
    implementation(libs.ivy)

    testImplementation("org.gradle:native")
    testImplementation("org.gradle:process-services")
    testImplementation("org.gradle:snapshots")

    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:model-core"))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":dependency-management")))

    integTestImplementation("org.gradle:ear")
    integTestImplementation(libs.slf4jApi)

    integTestRuntimeOnly("org.gradle:api-metadata")

    integTestRuntimeOnly("org.gradle:resources-s3")
    integTestRuntimeOnly("org.gradle:resources-sftp")

    testFixturesApi("org.gradle:base-services") {
        because("Test fixtures export the Action class")
    }
    testFixturesApi("org.gradle:core-api") {
        because("Test fixtures export the RepositoryHandler class")
    }
    testFixturesImplementation("org.gradle:logging")
    testFixturesImplementation(project(":dependency-management"))
    testFixturesImplementation("org.gradle:internal-integ-testing")
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.sshdCore)
    testFixturesImplementation(libs.sshdScp)
    testFixturesImplementation(libs.sshdSftp)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly("org.gradle:distributions-jvm")
    crossVersionTestDistributionRuntimeOnly(project(":distributions-core"))
}

integTest.usesSamples.set(true)
