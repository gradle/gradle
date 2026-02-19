rootProject.name = "my-app"

// tag::mag[]
pluginManagement {
    includeBuild("my-plugin")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
// end::mag[]

include("app")
