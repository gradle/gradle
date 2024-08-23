plugins {
    id("gradlebuild.distribution.api-java")
}

description = "A set of generic services and utilities specific for Groovy."

dependencies {
    api(projects.baseServices)
    api(projects.stdlibJavaExtensions)

    api(libs.jsr305)
    api(libs.groovy)
    api(libs.guava)

    testImplementation(testFixtures(projects.core))
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
