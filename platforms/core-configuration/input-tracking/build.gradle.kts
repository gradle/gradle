plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Configuration input discovery code"

dependencies {
    api(libs.jspecify)
    api(libs.guava)
}
