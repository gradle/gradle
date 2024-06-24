plugins {
    id("gradlebuild.distribution.api-java")
}

description = """Extends platform-base with base types and interfaces specific to the Java Virtual Machine, including tasks for obtaining a JDK via toolchains, and for compiling and launching Java applications."""

errorprone {
    disabledChecks.addAll(
        "StringCharset", // 1 occurrences
        "UnusedMethod", // 1 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":file-collections"))
    api(project(":logging"))
    api(project(":model-core"))
    api(project(":platform-base"))

    api(libs.groovy)
    api(libs.inject)
    api(libs.jsr305)
    api(libs.nativePlatform)

    implementation(project(":dependency-management"))
    implementation(project(":execution"))
    implementation(project(":functional"))
    implementation(project(":jvm-services"))
    implementation(project(":publish"))

    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)

    testImplementation(project(":snapshots"))
    testImplementation(libs.ant)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":logging")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":platform-native")))

    integTestImplementation(project(":internal-integ-testing"))

    integTestImplementation(libs.slf4jApi)

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

strictCompile {
    ignoreDeprecations() // most of this project has been deprecated
}

integTest.usesJavadocCodeSnippets = true
