kotlinBuildLogic {

    description = "Provides a plugin to define the version and name for subproject publications"

    dependencies {
        api(platform(project(":build-platform")))
        implementation(project(":basics"))
        implementation(catalog("buildLibs.gson"))
    }
}
