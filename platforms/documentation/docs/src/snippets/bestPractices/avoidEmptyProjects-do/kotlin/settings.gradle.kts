rootProject.name = "avoidEmptyProjects-do"

// tag::do-this[]
include(":app")

include(":my-web-module")
project(":my-web-module").projectDir = file("subs/web/my-web-module") // <1>
// end::do-this[]
