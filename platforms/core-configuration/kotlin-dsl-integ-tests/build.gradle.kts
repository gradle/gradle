plugins {
    id("gradlebuild.internal.kotlin")
    id("gradlebuild.kotlin-dsl-plugin-bundle-integ-tests")
}

description = "Kotlin DSL Integration Tests"

dependencies {
    testImplementation(testFixtures(project(":kotlin-dsl")))

    integTestImplementation(projects.messaging)
    integTestImplementation(project(":base-services"))
    integTestImplementation(project(":core-api"))
    integTestImplementation(project(":core"))
    integTestImplementation(project(":model-core"))
    integTestImplementation(project(":internal-testing"))
    integTestImplementation(project(":logging"))
    integTestImplementation(project(":language-jvm"))
    integTestImplementation(project(":platform-jvm"))
    integTestImplementation(libs.mockwebserver) {
        exclude(group = "org.bouncycastle").because("MockWebServer uses a different version of BouncyCastle")
    }
    integTestImplementation(libs.kotlinCompilerEmbeddable)
    integTestImplementation(libs.mockitoKotlin)

    integTestDistributionRuntimeOnly(project(":distributions-full"))

    crossVersionTestImplementation(project(":core-api"))
    crossVersionTestImplementation(project(":logging"))

    crossVersionTestDistributionRuntimeOnly(project(":distributions-full"))
    crossVersionTestLocalRepository(project(":kotlin-dsl-plugins"))
}

testFilesCleanup.reportOnly = true
