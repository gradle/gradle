include ("project-a", "project-b")

// tag::lookup-project[]
println(rootProject.name)
println(project(":project-a").name)
// end::lookup-project[]

// tag::change-project[]
rootProject.name = "main"
project(":project-a").projectDir = file("my-project-a")
project(":project-a").buildFileName = "project-a.gradle"
// end::change-project[]
