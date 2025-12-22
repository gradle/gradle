plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Utility code shared between the wrapper and the Gradle distribution"

gradleModule {
    targetRuntimes {
        usedInClient = true
    }
}

dependencies {
    implementation(projects.files) {
        because("We need org.gradle.internal.file.PathTraversalChecker")
    }

    api(libs.jspecify)

    testImplementation(projects.baseServices)
    testImplementation(projects.coreApi)
    testImplementation(projects.native)
    testImplementation(libs.commonsCompress)

    compileOnly(libs.jetbrainsAnnotations)

    integTestImplementation(projects.dependencyManagement)
    integTestImplementation(projects.logging)

    integTestDistributionRuntimeOnly(projects.distributionsFull)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}

errorprone {
    nullawayEnabled = true
}
