plugins {
    id("gradlebuild.distribution.packaging")
}

description = "The collector project for the 'native' portion of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(projects.corePlatform))

    agentsRuntimeOnly(projects.instrumentationAgent)

    pluginsRuntimeOnly(platform(projects.distributionsJvm)) {
        because("the project dependency 'toolingNative -> ide' currently links this to the JVM ecosystem")
    }
    pluginsRuntimeOnly(platform(projects.distributionsPublishing)) {
        because("configuring publishing is part of the 'language native' support")
    }

    pluginsRuntimeOnly(projects.languageNative)
    pluginsRuntimeOnly(projects.toolingNative)
    pluginsRuntimeOnly(projects.ideNative)
    pluginsRuntimeOnly(projects.testingNative)
}
