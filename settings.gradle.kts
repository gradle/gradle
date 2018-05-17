enableFeaturePreview("STABLE_PUBLISHING")

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
        buildFileName = "build.gradle.kts"
        assert(projectDir.isDirectory)
        assert(buildFile.isFile)
    }
}
