plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Public API for extending the build cache"

dependencies {
    implementation(project(":base-annotations"))
    implementation(libs.slf4jApi)

    integTestImplementation(project(":logging"))
    integTestDistributionRuntimeOnly(project(":distributions-core"))
}
