// tag::do-this[]
pluginManagement {
    includeBuild("build-logic") // <1>
}
// end::do-this[]

include(":project-a", ":project-b")

rootProject.name = "useConventionPlugins-do"
