plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Public API for extending the build cache"

dependencies {
    implementation(libs.jspecify)

    integTestImplementation(projects.logging)
    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
