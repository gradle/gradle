plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Adds support for assembling web application EAR files"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 2 occurrences
        "StringCaseLocaleUsage", // 1 occurrences
    )
}

dependencies {
    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)

    api(project(":base-services"))
    api(project(":core-api"))
    api(project(":language-jvm"))
    api(project(":model-core"))
    api(project(":platform-jvm"))

    implementation(projects.javaLanguageExtensions)
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":execution"))
    implementation(project(":file-collections"))
    implementation(project(":language-java"))
    implementation(project(":logging"))
    implementation(project(":platform-base"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-java-base"))

    implementation(libs.groovyXml)
    implementation(libs.guava)
    implementation(libs.commonsLang)

    testImplementation(project(":base-services-groovy"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(project(":native"))
    testImplementation(project(":war"))
    testImplementation(libs.ant)

    testRuntimeOnly(project(":distributions-jvm")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

packageCycles {
    excludePatterns.add("org/gradle/plugins/ear/internal/*")
}
