plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins and model builders for integration with Eclipse and IntelliJ IDEs"

errorprone {
    disabledChecks.addAll(
        "EmptyBlockTag", // 1 occurrences
        "LoopOverCharArray", // 1 occurrences
        "ObjectEqualsForPrimitives", // 3 occurrences
        "StringCaseLocaleUsage", // 2 occurrences
    )
}

dependencies {
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":file-collections"))
    api(project(":java-language-extensions"))
    api(project(":model-core"))
    api(project(":platform-jvm"))
    api(project(":service-provider"))
    api(project(":tooling-api"))

    api(libs.guava)
    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    implementation(project(":base-services-groovy"))
    implementation(project(":ear"))
    implementation(project(":language-java"))
    implementation(project(":logging-api"))
    implementation(project(":platform-base"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-java-base"))
    implementation(project(":war"))

    implementation(libs.groovyXml)
    implementation(libs.slf4jApi)
    implementation(libs.commonsIo)


    runtimeOnly(project(":language-jvm"))
    runtimeOnly(project(":testing-base"))
    runtimeOnly(project(":testing-jvm"))

    testFixturesApi(project(":base-services")) {
        because("test fixtures export the Action class")
    }
    testFixturesApi(project(":logging")) {
        because("test fixtures export the ConsoleOutput class")
    }
    testFixturesApi(project(":tooling-api")) {
        because("test fixtures export the EclipseWorkspace and EclipseWorkspaceProject classes")
    }
    testFixturesImplementation(project(":dependency-management"))
    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(project(":model-core"))
    testFixturesImplementation(libs.groovyXml)
    testFixturesImplementation(libs.xmlunit)

    testImplementation(project(":dependency-management"))
    testImplementation(libs.xmlunit)
    testImplementation(libs.equalsverifier)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":dependency-management")))
    testImplementation(testFixtures(project(":language-groovy")))

    testRuntimeOnly(project(":distributions-jvm")) {
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
