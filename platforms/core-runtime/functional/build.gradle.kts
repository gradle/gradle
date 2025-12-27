plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools to work with functional code, including data structures"

dependencies {
    api(libs.jspecify)
    api(projects.stdlibJavaExtensions)

    implementation(libs.guava)
    implementation(libs.fastutil)
    implementation(libs.jsr305)
}

errorprone {
    nullawayEnabled = true
}
