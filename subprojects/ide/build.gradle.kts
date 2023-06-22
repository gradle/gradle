plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins and model builders for integration with Eclipse and IntelliJ IDEs"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":file-collections"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":base-services-groovy"))
    implementation(project(":dependency-management"))
    implementation(project(":plugins"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-jvm"))
    implementation(project(":language-java"))
    implementation(project(":scala"))
    implementation(project(":ear"))
    implementation(project(":war"))
    implementation(project(":tooling-api"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))

    implementation(libs.groovy)
    implementation(libs.groovyXml)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.inject)

    testFixturesApi(project(":base-services")) {
        because("test fixtures export the Action class")
    }
    testFixturesApi(project(":logging")) {
        because("test fixtures export the ConsoleOutput class")
    }
    testFixturesApi(project(":tooling-api")) {
        because("test fixtures export the EclipseWorkspace and EclipseWorkspaceProject classes")
    }
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(libs.groovyXml)

    testImplementation(project(":dependency-management"))
    testImplementation(libs.xmlunit)
    testImplementation(libs.equalsverifier)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":language-groovy")))

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes()
}

packageCycles {
    excludePatterns.add("org/gradle/plugins/ide/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/eclipse/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/idea/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/eclipse/model/internal/*")
    excludePatterns.add("org/gradle/plugins/ide/idea/model/internal/*")
}

integTest.usesJavadocCodeSnippets = true
testFilesCleanup.reportOnly = true
