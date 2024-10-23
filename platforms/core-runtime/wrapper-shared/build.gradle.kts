plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utility code shared between the wrapper and the Gradle distribution"

gradlebuildJava.usedInWorkers()

dependencies {

    api(projects.stdlibJavaExtensions)

    implementation(projects.files) {
        because("We need org.gradle.internal.file.PathTraversalChecker")
    }

    testImplementation(projects.baseServices)
    testImplementation(projects.coreApi)
    testImplementation(projects.native)
    testImplementation(libs.commonsCompress)

    integTestImplementation(projects.dependencyManagement)
    integTestImplementation(projects.logging)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
