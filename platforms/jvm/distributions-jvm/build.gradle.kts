plugins {
    id("gradlebuild.distribution.packaging")
}

description = "The collector project for the 'jvm' portion of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))

    agentsRuntimeOnly(project(":instrumentation-agent"))

    pluginsRuntimeOnly(platform(project(":distributions-basics")))

    pluginsRuntimeOnly(project(":base-ide-plugins"))
    pluginsRuntimeOnly(project(":code-quality"))
    pluginsRuntimeOnly(project(":ear"))
    pluginsRuntimeOnly(project(":ide"))
    pluginsRuntimeOnly(project(":ide-plugins"))
    pluginsRuntimeOnly(project(":jacoco"))
    pluginsRuntimeOnly(project(":plugins-groovy"))
    pluginsRuntimeOnly(project(":plugins-java"))
    pluginsRuntimeOnly(project(":plugins-java-base"))
    pluginsRuntimeOnly(project(":plugins-jvm-test-suite"))
    pluginsRuntimeOnly(project(":plugins-test-report-aggregation"))
    pluginsRuntimeOnly(project(":scala"))
    pluginsRuntimeOnly(project(":war"))

    pluginsRuntimeOnly(project(":java-platform")) {
        because("Aspirationally, we likely need a platform-base plugin that would ship in the same distribution as dependency-management, and isn't java specific - unfortunately this plugin applies the JvmEcosystemPlugin.")
    }
}
