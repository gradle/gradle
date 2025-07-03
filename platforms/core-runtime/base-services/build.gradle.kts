import gradlebuild.basics.buildCommitId
import gradlebuild.identity.tasks.BuildReceipt

plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "A set of generic services and utilities."

gradleModule {
    targetRuntimes {
        usedInWorkers = true
    }
}

jvmCompile {
    compilations {
        named("main") {
            usesFutureStdlib = true
        }
    }
}

dependencies {
    api(projects.buildOperations)
    api(projects.classloaders)
    api(projects.concurrent)
    api(projects.fileTemp)
    api(projects.hashing)
    api(projects.serviceLookup)
    api(projects.stdlibJavaExtensions)

    api(libs.inject)
    api(libs.jspecify)
    api(libs.guava)

    implementation(projects.time)
    implementation(projects.baseAsm)

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.jsr305)
    implementation(libs.slf4jApi)

    integTestImplementation(projects.logging)

    testFixturesApi(projects.hashing)
    testFixturesImplementation(libs.guava)
    testImplementation(testFixtures(projects.core))
    testImplementation(libs.xerces)

    integTestDistributionRuntimeOnly(projects.distributionsCore)

    jmh(platform(projects.distributionsDependencies))
    jmh(libs.bouncycastleProvider)
    jmh(libs.guava)
}

packageCycles {
    // Needed for the factory methods in the base class
    excludePatterns.add("org/gradle/util/GradleVersion**")
}

jmh.includes = listOf("HashingAlgorithmsBenchmark")

tasks.isolatedProjectsIntegTest {
    enabled = false
}

// TODO: Base services should not be responsible for generating the build receipt.
//       Perhaps :api-metadata is a better fit
val createBuildReceipt by tasks.registering(BuildReceipt::class) {
    this.version = gradleModule.identity.version.map { it.version }
    this.baseVersion = gradleModule.identity.version.map { it.baseVersion.version }
    this.snapshot = gradleModule.identity.snapshot
    this.promotionBuild = gradleModule.identity.promotionBuild
    this.buildTimestampFrom(gradleModule.identity.buildTimestamp)
    this.commitId = project.buildCommitId
    this.receiptFolder = project.layout.buildDirectory.dir("generated-resources/build-receipt")
}

tasks.named<Jar>("jar").configure {
    from(createBuildReceipt.map { it.receiptFolder })
}
