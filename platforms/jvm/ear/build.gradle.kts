plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-java-project")
}

description = "Adds support for assembling web application EAR files"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 2 occurrences
    )
}

dependencies {
    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    api(projects.baseServices)
    api(projects.coreApi)
    api(projects.languageJvm)
    api(projects.modelCore)
    api(projects.platformJvm)

    implementation(projects.serviceLookup)
    implementation(projects.stdlibJavaExtensions)
    implementation(projects.core)
    implementation(projects.dependencyManagement)
    implementation(projects.execution)
    implementation(projects.fileCollections)
    implementation(projects.languageJava)
    implementation(projects.logging)
    implementation(projects.platformBase)
    implementation(projects.pluginsJava)
    implementation(projects.pluginsJavaBase)

    implementation(libs.groovyXml)
    implementation(libs.guava)
    implementation(libs.commonsLang)

    testImplementation(projects.baseServicesGroovy)
    testImplementation(testFixtures(projects.core))
    testImplementation(projects.native)
    testImplementation(projects.war)
    testImplementation(libs.ant)

    testRuntimeOnly(projects.distributionsJvm) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

packageCycles {
    excludePatterns.add("org/gradle/plugins/ear/internal/*")
}
