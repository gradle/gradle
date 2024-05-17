plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Annotation classes used by the Declarative DSL"

dependencies {
    implementation(projects.javaLanguageExtensions)
}
