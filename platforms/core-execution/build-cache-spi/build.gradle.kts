plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Public API for extending the build cache"

dependencies {
    compileOnly(libs.jspecify)

    integTestImplementation(projects.logging)
    integTestDistributionRuntimeOnly(projects.distributionsCore)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}

errorprone {
    nullawayEnabled = true
}
