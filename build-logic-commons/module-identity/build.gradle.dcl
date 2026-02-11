kotlinBuildLogic {

    description = "Provides a plugin to define the version and name for subproject publications"

    dependencies {
        api(platformProject(":build-platform"))
        implementation(project(":basics"))
        implementation(catalog("buildLibs.gson"))
    }
}
