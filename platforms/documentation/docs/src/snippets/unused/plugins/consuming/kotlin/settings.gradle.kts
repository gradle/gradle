// tag::custom-plugin-repositories[]
pluginManagement {
    repositories {
        maven(url = file("./maven-repo"))
        gradlePluginPortal()
        ivy(url = file("./ivy-repo"))
    }
}
// end::custom-plugin-repositories[]

rootProject.name = "custom-repositories"
