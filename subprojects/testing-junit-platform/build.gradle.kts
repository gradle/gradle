plugins {
    id("gradlebuild.distribution.api-java")
}


description = """Support class used to integrate running tests with the JUnit Jupiter testing framework into a Gradle build.

This project should NOT be used as an implementation dependency anywhere."""

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-java"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))

    implementation(libs.junitPlatform)
}
