plugins {
    gradlebuild.distribution.packaging
}

dependencies {
    coreRuntimeOnly(platform(project(":corePlatform")))

    pluginsRuntimeOnly(project(":pluginUse")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    pluginsRuntimeOnly(project(":dependencyManagement")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    pluginsRuntimeOnly(project(":workers")) {
        because("This is a core extension module (see DynamicModulesClassPathProvider.GRADLE_EXTENSION_MODULES)")
    }
    pluginsRuntimeOnly(project(":compositeBuilds")) {
        because("We always need a BuildStateRegistry service implementation for certain code in ':core' to work.")
    }
    pluginsRuntimeOnly(project(":versionControl")) {
        because("We always need a VcsMappingsStore service implementation to create 'ConfigurationContainer' in ':dependency-management'.")
    }
    pluginsRuntimeOnly(project(":instantExecution")) {
        because("We always need a BuildLogicTransformStrategy service implementation.")
    }
    pluginsRuntimeOnly(project(":testingJunitPlatform")) {
        because("All test workers have JUnit platform on their classpath (see ForkingTestClassProcessor.getTestWorkerImplementationClasspath).")
    }
    pluginsRuntimeOnly(project(":kotlinDslProviderPlugins")) {
        because("We need a KotlinScriptBasePluginsApplicator service implementation to use Kotlin DSL scripts.")
    }
}
