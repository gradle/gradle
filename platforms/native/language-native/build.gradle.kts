plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins and domain objects for building different native languages"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
        "JavaLangClash", // 1 occurrences
        "MixedMutabilityReturnType", // 1 occurrences
        "StringCaseLocaleUsage", // 1 occurrences
        "UnusedMethod", // 2 occurrences
        "UnusedVariable", // 10 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":concurrent"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":dependency-management"))
    api(project(":files"))
    api(project(":file-collections"))
    api(project(":file-temp"))
    api(project(":hashing"))
    api(project(":java-language-extensions"))
    api(project(":model-core"))
    api(project(":persistent-cache"))
    api(project(":platform-base"))
    api(project(":platform-native"))
    api(project(":serialization"))
    api(project(":snapshots"))

    api(libs.guava)
    api(libs.jsr305)
    api(libs.inject)

    implementation(project(":logging-api"))
    implementation(project(":maven"))
    implementation(project(":process-services"))
    implementation(project(":publish"))
    implementation(project(":version-control"))

    implementation(libs.commonsLang)
    implementation(libs.groovy)
    implementation(libs.slf4jApi)

    testFixturesApi(project(":base-services")) {
        because("Test fixtures export the Named class")
    }
    testFixturesApi(project(":platform-base")) {
        because("Test fixtures export the Platform class")
    }

    testFixturesImplementation(project(":internal-integ-testing"))
    testFixturesImplementation(testFixtures(project(":platform-native")))

    testImplementation(project(":native"))
    testImplementation(project(":resources"))
    testImplementation(project(":base-services-groovy"))
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(projects.serialization))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":version-control")))
    testImplementation(testFixtures(project(":platform-native")))
    testImplementation(testFixtures(project(":platform-base")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":snapshots")))

    integTestImplementation(project(":native"))
    integTestImplementation(project(":enterprise-operations"))
    integTestImplementation(project(":resources"))
    integTestImplementation(libs.nativePlatform)
    integTestImplementation(libs.ant)
    integTestImplementation(libs.jgit)

    testRuntimeOnly(project(":distributions-core")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-native"))
}

packageCycles {
    excludePatterns.add("org/gradle/language/nativeplatform/internal/**")
}

integTest.usesJavadocCodeSnippets = true

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
