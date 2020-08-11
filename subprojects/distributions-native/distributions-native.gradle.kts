plugins {
    id("gradlebuild.distribution.packaging")
}

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))

    pluginsRuntimeOnly(platform(project(":distributions-jvm"))) {
        because("the project dependency 'toolingNative -> ide' currently links this to the JVM ecosystem")
    }
    pluginsRuntimeOnly(platform(project(":distributions-publishing"))) {
        because("configuring publishing is part of the 'language native' support")
    }

    pluginsRuntimeOnly(project(":language-native"))
    pluginsRuntimeOnly(project(":tooling-native"))
    pluginsRuntimeOnly(project(":ide-native"))
    pluginsRuntimeOnly(project(":testing-native"))
}
