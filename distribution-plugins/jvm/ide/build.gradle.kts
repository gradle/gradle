plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:process-services")
    implementation("org.gradle:file-collections")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:base-services-groovy")
    implementation("org.gradle:tooling-api")

    implementation("org.gradle:dependency-management")
    implementation("org.gradle:plugins")
    implementation("org.gradle:platform-base")
    implementation("org.gradle:platform-jvm")
    implementation("org.gradle:language-jvm")
    implementation("org.gradle:language-java")
    implementation("org.gradle:scala")
    implementation("org.gradle:ear")

    implementation(libs.groovy)
    implementation(libs.groovyXml)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.inject)

    testFixturesApi("org.gradle:base-services") {
        because("test fixtures export the Action class")
    }
    testFixturesApi("org.gradle:logging") {
        because("test fixtures export the ConsoleOutput class")
    }
    testFixturesImplementation("org.gradle:internal-integ-testing")
    testFixturesImplementation(libs.groovyXml)

    testImplementation("org.gradle:dependency-management")
    testImplementation(libs.xmlunit)
    testImplementation(libs.equalsverifier)
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:dependency-management"))

    testRuntimeOnly("org.gradle:distributions-core") {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes()
}

classycle {
    excludePatterns.add("org/gradle/plugins/ide/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/eclipse/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/idea/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/eclipse/model/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/idea/model/internal/*")
}

integTest.usesSamples.set(true)
testFilesCleanup.reportOnly.set(true)
