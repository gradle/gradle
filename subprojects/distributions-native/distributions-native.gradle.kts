plugins {
    gradlebuild.distribution.packaging
}

dependencies {
    coreRuntimeOnly(platform(project(":corePlatform")))

    pluginsRuntimeOnly(platform(project(":distributionsJvm"))) {
        because("the project dependency 'toolingNative -> ide' currently links this to the JVM ecosystem")
    }
    pluginsRuntimeOnly(platform(project(":distributionsPublishing"))) {
        because("configuring publishing is part of the 'language native' support")
    }

    pluginsRuntimeOnly(project(":languageNative"))
    pluginsRuntimeOnly(project(":toolingNative"))
    pluginsRuntimeOnly(project(":ideNative"))
    pluginsRuntimeOnly(project(":testingNative"))
}
