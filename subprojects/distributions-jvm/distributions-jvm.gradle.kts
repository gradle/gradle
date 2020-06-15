plugins {
    gradlebuild.distribution.packaging
}

dependencies {
    coreRuntimeOnly(platform(project(":corePlatform")))

    pluginsRuntimeOnly(platform(project(":distributionsBasics")))

    pluginsRuntimeOnly(project(":scala"))
    pluginsRuntimeOnly(project(":ear"))
    pluginsRuntimeOnly(project(":codeQuality"))
    pluginsRuntimeOnly(project(":jacoco"))
    pluginsRuntimeOnly(project(":ide"))
}
