plugins {
    id("gradlebuild.distribution.packaging")
    id("gradlebuild.verify-build-environment")
    id("gradlebuild.install")
}

description = "The collector project for the entirety of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))

    agentsRuntimeOnly(project(":instrumentation-agent"))

    pluginsRuntimeOnly(platform(project(":distributions-publishing")))
    pluginsRuntimeOnly(platform(project(":distributions-jvm")))
    pluginsRuntimeOnly(platform(project(":distributions-native")))

    pluginsRuntimeOnly(project(":plugin-development"))
    pluginsRuntimeOnly(project(":build-configuration"))
    pluginsRuntimeOnly(project(":build-init"))
    pluginsRuntimeOnly(project(":build-profile"))
    pluginsRuntimeOnly(project(":antlr"))
    pluginsRuntimeOnly(project(":enterprise"))
    pluginsRuntimeOnly(project(":unit-test-fixtures"))
}

// This is required for the separate promotion build and should be adjusted there in the future
tasks.register<Copy>("copyDistributionsToRootBuild") {
    dependsOn("buildDists")
    from(layout.buildDirectory.dir("distributions"))
    into(rootProject.layout.buildDirectory.dir("distributions"))
}
