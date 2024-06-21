plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Public API for extending the build cache"

dependencies {
    implementation(projects.stdlibJavaExtensions)

    integTestImplementation(project(":logging"))
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
