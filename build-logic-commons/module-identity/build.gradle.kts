plugins {
    `kotlin-dsl`
}

description = "Provides a plugin to define the version and name for subproject publications"

group = "gradlebuild"

dependencies {
    api(platform(projects.buildPlatform))

    api(buildLibs.pomPropertiesPlugin) {
        because("downstream build-logic projects use the GeneratePomProperties task type for the distribution jars that are not the standard `jar` (shaded, ABI, metadata jars)")
    }

    implementation(projects.basics)

    implementation(buildLibs.gson)
}
