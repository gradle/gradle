// tag::repositories[]
pluginManagement {
    repositories {
        jcenter()
        gradlePluginPortal()
    }
}
// end::repositories[]

// tag::base[]
rootProject.name = "multi-project-build"
include("domain", "infra", "http")
// end::base[]
