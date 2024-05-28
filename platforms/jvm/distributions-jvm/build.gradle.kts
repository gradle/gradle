plugins {
    id("gradlebuild.distribution.packaging")
}

description = "The collector project for the 'jvm' portion of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(projects.corePlatform))

    agentsRuntimeOnly(projects.instrumentationAgent)

    pluginsRuntimeOnly(platform(projects.distributionsBasics))

    pluginsRuntimeOnly(projects.baseIdePlugins)
    pluginsRuntimeOnly(projects.codeQuality)
    pluginsRuntimeOnly(projects.ear)
    pluginsRuntimeOnly(projects.ide)
    pluginsRuntimeOnly(projects.idePlugins)
    pluginsRuntimeOnly(projects.jacoco)
    pluginsRuntimeOnly(projects.pluginsJavaApplication)
    pluginsRuntimeOnly(projects.pluginsGroovy)
    pluginsRuntimeOnly(projects.pluginsJava)
    pluginsRuntimeOnly(projects.pluginsJavaBase)
    pluginsRuntimeOnly(projects.pluginsJavaLibrary)
    pluginsRuntimeOnly(projects.pluginsJvmTestFixtures)
    pluginsRuntimeOnly(projects.pluginsJvmTestSuite)
    pluginsRuntimeOnly(projects.pluginsTestReportAggregation)
    pluginsRuntimeOnly(projects.pluginsVersionCatalog)
    pluginsRuntimeOnly(projects.scala)
    pluginsRuntimeOnly(projects.war)

    pluginsRuntimeOnly(projects.javaPlatform) {
        because("Aspirationally, we likely need a platform-base plugin that would ship in the same distribution as dependency-management, and isn't java specific - unfortunately this plugin applies the JvmEcosystemPlugin.")
    }
}
