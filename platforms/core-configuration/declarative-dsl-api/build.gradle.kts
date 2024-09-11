plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Annotation classes used by the Declarative DSL"

dependencies {
    implementation(projects.stdlibJavaExtensions)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
