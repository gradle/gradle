plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Kotlin DSL Tooling Builders for IDEs"

dependencies {
    implementation("org.gradle:kotlin-dsl")

    implementation("org.gradle:base-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:resources")
    implementation("org.gradle:tooling-api")
    implementation("org.gradle:logging")

    implementation("org.gradle:platform-base")
    implementation("org.gradle:platform-jvm")
    implementation("org.gradle:plugins")

    testImplementation(testFixtures("org.gradle:kotlin-dsl"))
    integTestImplementation("org.gradle:internal-testing")

    crossVersionTestImplementation("org.gradle:persistent-cache")
    crossVersionTestImplementation(libs.slf4jApi)
    crossVersionTestImplementation(libs.guava)
    crossVersionTestImplementation(libs.ant)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-basics"))
}

testFilesCleanup.reportOnly.set(true)
