plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Internal implementation of Gradle authentication container"

dependencies {
    api(projects.baseServices)
    api(projects.core)                 // DefaultPolymorphicDomainObjectContainer
    api(projects.coreApi)              // AuthenticationContainer, CollectionCallbackActionDecorator
    api(projects.credentials)          // AuthenticationSchemeRegistry
    api(projects.credentialsApi)       // Authentication interface
    api(projects.serviceProvider)      // AbstractGradleModuleServices
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.stdlibJavaExtensions)  // @NullMarked

    testImplementation(testFixtures(projects.core))
}
