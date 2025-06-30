rootProject.name = "avoidEmptyProjects-do"

// tag::do-this[]
include(":app")

include(":subs:web:my-web-module")
project(":subs:web:my-web-module").projectDir = file("subs/web/my-web-module") // <1>
// end::do-this[]
