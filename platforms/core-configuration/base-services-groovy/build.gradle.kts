plugins {
    id("gradlebuild.distribution.api-java")
}

description = "A set of generic services and utilities specific for Groovy."

dependencies {
    api(project(":base-services"))
    api(projects.javaLanguageExtensions)

    api(libs.jsr305)
    api(libs.groovy)
    api(libs.guava)

    testImplementation(testFixtures(project(":core")))
}
