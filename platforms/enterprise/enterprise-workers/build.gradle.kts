plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Develocity plugin dependencies that also need to be exposed to workers"

dependencies {
    api(libs.jspecify)
}

errorprone {
    nullawayEnabled = true
}
