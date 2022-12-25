plugins {
    id("gradlebuild.distribution.packaging")
}

description = "The collector project for the 'publishing' portion of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))

    agentsRuntimeOnly(project(":instrumentation-agent"))

    pluginsRuntimeOnly(platform(project(":distributions-basics")))

    pluginsRuntimeOnly(project(":signing"))
}
