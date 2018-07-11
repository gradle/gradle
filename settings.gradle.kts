enableFeaturePreview("STABLE_PUBLISHING")

//TODO:kotlin-eap - remove after the next snapshot distro upgrade
pluginManagement {
    repositories {
        kotlinDev()
        gradlePluginPortal()
    }
}

gradle.allprojects {
    repositories { kotlinDev() }
}

rootProject.name = "gradle-kotlin-dsl"

include(
    "provider",
    "provider-plugins",
    "tooling-models",
    "tooling-builders",
    "plugins",
    "plugins-experiments",
    "test-fixtures",
    "samples-tests",
    "integ-tests")

for (project in rootProject.children) {
    project.apply {
        projectDir = file("subprojects/$name")
        buildFileName = "$name.gradle.kts"
        assert(projectDir.isDirectory)
        assert(buildFile.isFile)
    }
}
