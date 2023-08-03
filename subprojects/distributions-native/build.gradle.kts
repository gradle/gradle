plugins {
    id("gradlebuild.distribution.packaging")
}

description = "The collector project for the 'native' portion of the Gradle distribution"

dependencies {
    coreRuntimeOnly(platform(project(":core-platform")))

    agentsRuntimeOnly(project(":instrumentation-agent"))

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
