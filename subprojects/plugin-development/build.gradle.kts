plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Gradle plugin development plugins"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":files"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":execution"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":maven"))
    implementation(project(":ivy"))
    implementation(project(":platform-jvm"))
    implementation(project(":reporting"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":plugins"))
    implementation(project(":plugin-use"))
    implementation(project(":publish"))
    implementation(project(":messaging"))
    implementation(project(":workers"))
    implementation(project(":model-groovy"))
    implementation(project(":resources"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.asm)

    testImplementation(project(":file-collections"))
    testImplementation(project(":enterprise-operations"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":logging")))

    integTestImplementation(project(":base-services-groovy"))
    integTestImplementation(libs.jetbrainsAnnotations)
    integTestImplementation(testFixtures(project(":model-core")))
    integTestImplementation(libs.groovyTest)
    integTestImplementation(testFixtures(project(":tooling-api")))

    integTestLocalRepository(project(":tooling-api")) {
        because("Required by GradleImplDepsCompatibilityIntegrationTest")
    }

    testRuntimeOnly(project(":distributions-basics")) {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly(project(":distributions-basics"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-basics"))

    testFixturesImplementation(project(":model-core"))
}

integTest.usesJavadocCodeSnippets = true

strictCompile {
    ignoreDeprecations()
}

// Remove as part of fixing https://github.com/gradle/configuration-cache/issues/585
tasks.configCacheIntegTest {
    systemProperties["org.gradle.configuration-cache.internal.test-disable-load-after-store"] = "true"
}
