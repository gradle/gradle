plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal implementation of Gradle authentication container"

dependencies {
    api(projects.baseServices)
    api(projects.core)                 // DefaultPolymorphicDomainObjectContainer
    api(projects.coreApi)              // AuthenticationContainer, CollectionCallbackActionDecorator
    api(projects.resources)            // Authentication interface
    api(libs.inject)

    testImplementation(projects.credentials)
    testImplementation(testFixtures(projects.core))
}
