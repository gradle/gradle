pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

apply(from = "gradle/shared-with-buildSrc/build-cache-configuration.settings.gradle.kts")

rootProject.name = "gradle-kotlin-dsl"

include(
    "provider",
    "provider-plugins",
    "tooling-models",
    "tooling-builders",
    "plugins",
    "test-fixtures",
    "samples-tests",
    "integ-tests"
)

for (project in rootProject.children) {
    project.apply {
        projectDir = file("subprojects/$name")
        buildFileName = "$name.gradle.kts"
        require(projectDir.isDirectory) { "Project '${project.path} must have a $projectDir directory" }
        require(buildFile.isFile) { "Project '${project.path} must have a $buildFile build script" }
    }
}
