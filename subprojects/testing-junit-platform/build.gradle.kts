plugins {
    id("gradlebuild.distribution.api-java")
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-java"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))

    implementation(libs.junit)
    implementation(libs.junitPlatform)
}
