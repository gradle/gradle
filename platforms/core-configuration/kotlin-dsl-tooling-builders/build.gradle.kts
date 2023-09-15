plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Kotlin DSL Tooling Builders for IDEs"

dependencies {
    implementation(project(":kotlin-dsl"))

    implementation(project(":base-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-java"))
    implementation(project(":tooling-api"))
    implementation(project(":logging"))

    testImplementation(testFixtures(project(":kotlin-dsl")))
    integTestImplementation(testFixtures(project(":tooling-api")))

    integTestImplementation(project(":internal-testing"))
    testFixturesImplementation(project(":internal-integ-testing"))

    crossVersionTestImplementation(project(":persistent-cache"))
    crossVersionTestImplementation(libs.slf4jApi)
    crossVersionTestImplementation(libs.guava)
    crossVersionTestImplementation(libs.ant)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-basics"))
}

testFilesCleanup.reportOnly = true
