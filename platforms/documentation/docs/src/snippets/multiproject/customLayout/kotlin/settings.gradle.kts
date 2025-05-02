// tag::change-project[]
rootProject.name = "main"
// tag::lookup-project[]
include("project-a")
// end::change-project[]
println(rootProject.name)
println(project(":project-a").name)
// end::lookup-project[]

// tag::change-project[]
project(":project-a").projectDir = file("custom/my-project-a")
project(":project-a").buildFileName = "project-a.gradle.kts"
// end::change-project[]
