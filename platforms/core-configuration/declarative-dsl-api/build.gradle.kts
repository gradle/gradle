plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Annotation classes used by the Declarative DSL"

dependencies {
    compileOnly(libs.jspecify)
}

errorprone {
    nullawayEnabled = true
}
