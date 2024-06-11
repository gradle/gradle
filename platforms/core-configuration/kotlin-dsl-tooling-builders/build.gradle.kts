plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Kotlin DSL Tooling Builders for IDEs"

dependencies {
    api(project(":core-api"))
    api(project(":core"))
    api(libs.kotlinStdlib)

    implementation(projects.javaLanguageExtensions)
    implementation(projects.time)
    implementation(project(":kotlin-dsl"))
    implementation(project(":base-services"))
    implementation(project(":resources"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":plugins-java-base"))
    implementation(project(":tooling-api"))
    implementation(project(":logging"))
    implementation(project(":kotlin-dsl-tooling-models"))
    implementation(project(":build-process-services"))

    testImplementation(testFixtures(project(":kotlin-dsl")))
    integTestImplementation(testFixtures(project(":tooling-api")))

    integTestImplementation(project(":internal-testing"))
    testFixturesImplementation(project(":internal-integ-testing"))

    crossVersionTestImplementation(project(":persistent-cache"))
    crossVersionTestImplementation(libs.slf4jApi)
    crossVersionTestImplementation(libs.guava)
    crossVersionTestImplementation(libs.ant)

    integTestDistributionRuntimeOnly(project(":distributions-basics"))
    crossVersionTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("Uses application plugin.")
    }
}

testFilesCleanup.reportOnly = true
