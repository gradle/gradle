import gradlebuild.basics.buildCommitId
import gradlebuild.identity.tasks.BuildReceipt

plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "A set of generic services and utilities."

gradlebuildJava.usedInWorkers()

/**
 * Use Java 8 compatibility for Unit tests, so we can test Java 8 features as well
 */
tasks.named<JavaCompile>("compileTestJava") {
    options.release = 8
}
afterEvaluate {
    tasks.named<GroovyCompile>("compileTestGroovy") {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
}

/**
 * Use Java 8 compatibility for JMH benchmarks
 */
tasks.named<JavaCompile>("jmhCompileGeneratedClasses") {
    options.release = 8
}

createBuildReceipt()

dependencies {
    api(projects.buildOperations)
    api(projects.classloaders)
    api(projects.concurrent)
    api(projects.fileTemp)
    api(projects.hashing)
    api(projects.serviceLookup)
    api(projects.stdlibJavaExtensions)
    api(libs.inject)
    api(libs.jsr305)
    api(libs.guava)

    implementation(projects.time)
    implementation(projects.baseAsm)

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
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

fun createBuildReceipt() {
    // TODO: Why is base-services in charge of generating the build receipt?
    //       There is no reason _this_ project should have this responsibility.

    val createBuildReceipt by tasks.registering(BuildReceipt::class) {
        version = moduleIdentity.version.map { it.version }
        baseVersion = moduleIdentity.version.map { it.baseVersion.version }
        snapshot = moduleIdentity.snapshot
        promotionBuild = moduleIdentity.promotionBuild
        buildTimestampFrom(moduleIdentity.buildTimestamp)
        commitId = project.buildCommitId
        receiptFolder = project.layout.buildDirectory.dir("generated-resources/build-receipt")
    }

    tasks.named<Jar>("jar").configure {
        from(createBuildReceipt.map { it.receiptFolder })
    }
}
