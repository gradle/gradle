plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:process-services")
    implementation("org.gradle:files")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:execution")
    implementation("org.gradle:core")
    implementation("org.gradle:messaging")
    implementation("org.gradle:model-groovy")
    implementation("org.gradle:resources")
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
    implementation(project(":workers"))

    implementation(libs.groovy)
    implementation(libs.guava)
    implementation(libs.inject)
    implementation(libs.asm)

    testImplementation("org.gradle:file-collections")
    testImplementation(testFixtures("org.gradle:core"))
    testImplementation(testFixtures("org.gradle:logging"))

    integTestImplementation("org.gradle:base-services-groovy")
    integTestImplementation(libs.jetbrainsAnnotations)
    integTestImplementation(testFixtures("org.gradle:model-core"))
    integTestImplementation(libs.groovyTest)

    integTestLocalRepository("org.gradle:tooling-api") {
        because("Required by GradleImplDepsCompatibilityIntegrationTest")
    }

    testRuntimeOnly("org.gradle:distributions-basics") {
        because("ProjectBuilder tests load services from a Gradle distribution.")
    }
    integTestDistributionRuntimeOnly("org.gradle:distributions-basics")
    crossVersionTestDistributionRuntimeOnly("org.gradle:distributions-basics")
}

integTest.usesSamples.set(true)
