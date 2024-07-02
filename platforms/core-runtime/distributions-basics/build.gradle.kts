plugins {
    id("gradlebuild.distribution.packaging")
}

description = "The collector project for the 'basics' portion of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(projects.corePlatform))

    agentsRuntimeOnly(projects.instrumentationAgent)

    pluginsRuntimeOnly(platform(projects.distributionsCore))

    pluginsRuntimeOnly(projects.resourcesHttp)
    pluginsRuntimeOnly(projects.resourcesSftp)
    pluginsRuntimeOnly(projects.resourcesS3)
    pluginsRuntimeOnly(projects.resourcesGcs)
    pluginsRuntimeOnly(projects.resourcesHttp)
    pluginsRuntimeOnly(projects.buildCacheHttp)

    pluginsRuntimeOnly(projects.toolingApiBuilders)
    pluginsRuntimeOnly(projects.declarativeDslToolingBuilders)
    pluginsRuntimeOnly(projects.kotlinDslToolingBuilders)

    pluginsRuntimeOnly(projects.testKit)
    pluginsRuntimeOnly(projects.unitTestFixtures)
}
