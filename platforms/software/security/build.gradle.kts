plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Shared classes for projects requiring GPG support"

dependencies {
    api(projects.resources)

    api(libs.bouncycastlePgp)
    api(libs.jsr305)

    implementation(projects.stdlibJavaExtensions)
    implementation(projects.time)
    implementation(projects.loggingApi)

    implementation(libs.bouncycastleProvider)
    implementation(libs.guava)
}
