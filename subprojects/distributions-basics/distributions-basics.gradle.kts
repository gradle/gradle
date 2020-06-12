plugins {
    gradlebuild.distribution.packaging
}

dependencies {
    coreRuntimeOnly(platform(project(":corePlatform")))

    pluginsRuntimeOnly(platform(project(":distributionsCore")))

    pluginsRuntimeOnly(project(":resourcesHttp"))
    pluginsRuntimeOnly(project(":resourcesSftp"))
    pluginsRuntimeOnly(project(":resourcesS3"))
    pluginsRuntimeOnly(project(":resourcesGcs"))
    pluginsRuntimeOnly(project(":resourcesHttp"))
    pluginsRuntimeOnly(project(":buildCacheHttp"))

    pluginsRuntimeOnly(project(":toolingApiBuilders"))
    pluginsRuntimeOnly(project(":kotlinDslToolingBuilders"))

    pluginsRuntimeOnly(project(":testKit"))
}
