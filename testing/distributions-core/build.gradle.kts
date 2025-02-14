plugins {
    id("gradlebuild.distribution.packaging")
}

description = "The collector project for the 'core' portion of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(projects.corePlatform))

    agentsRuntimeOnly(projects.instrumentationAgent)

    pluginsRuntimeOnly(projects.pluginUse) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    pluginsRuntimeOnly(projects.dependencyManagement) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    pluginsRuntimeOnly(projects.softwareDiagnostics) {
        because("This should travel with dependency management.")
    }
    pluginsRuntimeOnly(projects.workers) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    pluginsRuntimeOnly(projects.compositeBuilds) {
        because("We always need a BuildStateRegistry service implementation for certain code in ':core' to work.")
    }
    pluginsRuntimeOnly(projects.toolingApiBuilders) {
        because("We always need a BuildEventListenerFactory service implementation for ':launcher' to create global services.")
    }
    pluginsRuntimeOnly(projects.versionControl) {
        because("We always need a VcsMappingsStore service implementation to create 'ConfigurationContainer' in ':dependency-management'.")
    }
    pluginsRuntimeOnly(projects.configurationCache) {
        because("We always need a BuildLogicTransformStrategy service implementation.")
    }
    pluginsRuntimeOnly(projects.testingJunitPlatform) {
        because("All test workers have JUnit platform on their classpath (see ForkingTestClassProcessor.getTestWorkerImplementationClasspath).")
    }
    pluginsRuntimeOnly(projects.kotlinDslProviderPlugins) {
        because("We need a KotlinScriptBasePluginsApplicator service implementation to use Kotlin DSL scripts.")
    }
    pluginsRuntimeOnly(projects.instrumentationDeclarations) {
        because("Property upgrades for core plugins reference types on plugin classpath and that is why interceptors need to be loaded from plugins' classpath.")
    }
    pluginsRuntimeOnly(projects.unitTestFixtures) {
        because("This is required for gradleApi()")
    }
}
