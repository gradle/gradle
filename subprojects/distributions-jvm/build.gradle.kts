plugins {
    id("gradlebuild.distribution.packaging")
}

description = "The collector project for the 'jvm' portion of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))

    agentsRuntimeOnly(project(":instrumentation-agent"))

    pluginsRuntimeOnly(platform(project(":distributions-basics")))

    pluginsRuntimeOnly(project(":scala"))
    pluginsRuntimeOnly(project(":ear"))
    pluginsRuntimeOnly(project(":code-quality"))
    pluginsRuntimeOnly(project(":jacoco"))
    pluginsRuntimeOnly(project(":ide"))
    pluginsRuntimeOnly(project(":ide-plugins"))
    pluginsRuntimeOnly(project(":war"))

    pluginsRuntimeOnly(project(":java-platform")) {
        because("Aspirationally, we likely need a platform-base plugin that would ship in the same distribution as dependency-management, and isn't java specific - unfortunately this plugin applies the JvmEcosystemPlugin.")
    }
}
