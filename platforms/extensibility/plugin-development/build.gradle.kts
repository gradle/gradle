plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Gradle plugin development plugins"

errorprone {
    disabledChecks.addAll(
        "DefaultCharset", // 1 occurrences
        "LoopOverCharArray", // 1 occurrences
    )
}

dependencies {
    api(project(":base-services"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":files"))
    api(project(":java-language-extensions"))
    api(project(":logging"))
    api(project(":model-core"))
    api(project(":platform-jvm"))
    api(project(":problems-api"))
    api(project(":resources"))
    api(project(":toolchains-jvm-shared"))
    api(project(":workers"))

    api(libs.groovy)
    api(libs.gson)
    api(libs.jsr305)
    api(libs.inject)

    implementation(projects.serviceProvider)
    implementation(project(":dependency-management"))
    implementation(project(":execution"))
    implementation(project(":hashing"))
    implementation(project(":ivy"))
    implementation(project(":language-java"))
    implementation(project(":language-jvm"))
    implementation(project(":logging-api"))
    implementation(project(":maven"))
    implementation(project(":messaging"))
    implementation(project(":model-groovy"))
    implementation(project(":plugins-groovy"))
    implementation(project(":plugins-java"))
    implementation(project(":plugins-java-base"))
    implementation(project(":plugins-java-library"))
    implementation(project(":plugins-jvm-test-suite"))
    implementation(project(":plugin-use"))
    implementation(project(":process-services"))
    implementation(project(":publish"))
    implementation(project(":testing-jvm"))
    implementation(project(":toolchains-jvm"))

    implementation(libs.asm)
    implementation(libs.guava)

    testImplementation(project(":file-collections"))
    testImplementation(project(":enterprise-operations"))

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    integTestImplementation(project(":base-services-groovy"))

    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(testFixtures(project(":tooling-api")))

    integTestImplementation(libs.groovyTest)
    integTestImplementation(libs.jetbrainsAnnotations)

    integTestLocalRepository(project(":tooling-api")) {
        because("Required by GradleImplDepsCompatibilityIntegrationTest")
    }

    testRuntimeOnly(project(":distributions-basics")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-basics"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-basics"))

    testFixturesImplementation(project(":model-core"))
    testFixturesImplementation(project(":logging"))
    testFixturesImplementation(libs.gson)
    testFixturesImplementation(project(":base-services"))
}

integTest.usesJavadocCodeSnippets = true

strictCompile {
    ignoreDeprecations()
}

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
