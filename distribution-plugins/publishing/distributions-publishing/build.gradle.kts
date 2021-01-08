plugins {
    id("gradlebuild.distribution.packaging")
}

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))

    pluginsRuntimeOnly(platform(project(":distributions-basics")))

    pluginsRuntimeOnly(project(":signing"))
}
