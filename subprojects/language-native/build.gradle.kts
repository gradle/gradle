plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Plugins and domain objects for building different native languages"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":files"))
    implementation(project(":file-collections"))
    implementation(project(":file-temp"))
    implementation(project(":persistent-cache"))
    implementation(project(":snapshots"))
    implementation(project(":dependency-management"))
    implementation(project(":platform-base"))
    implementation(project(":platform-native"))
    implementation(project(":plugins"))
    implementation(project(":publish"))
    implementation(project(":maven"))
    implementation(project(":ivy"))
    implementation(project(":tooling-api"))
    implementation(project(":version-control"))

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.inject)

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

