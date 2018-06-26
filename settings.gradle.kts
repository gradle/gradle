enableFeaturePreview("STABLE_PUBLISHING")

fun RepositoryHandler.kotlinDev() =
    maven(url = "https://dl.bintray.com/kotlin/kotlin-dev/")

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
