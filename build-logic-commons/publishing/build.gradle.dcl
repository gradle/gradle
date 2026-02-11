kotlinBuildLogic {

    description = "Provides a plugin for publishing some of Gradle's subprojects to Artifactory or the Plugin Portal"

    dependencies {
        implementation(project(":basics"))
        implementation(project(":module-identity"))
        implementation(catalog("buildLibs.publishPlugin"))
    }
}
