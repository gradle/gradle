plugins {
    id("gradlebuild.distribution.packaging")
}

description = "The collector project for the 'publishing' portion of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(projects.corePlatform))

    agentsRuntimeOnly(projects.instrumentationAgent)

    pluginsRuntimeOnly(platform(projects.distributionsBasics))

    pluginsRuntimeOnly(projects.signing)
}
