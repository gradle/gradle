// tag::declare-task[]
tasks.register<Copy>("myCopy")
// end::declare-task[]

// tag::configure[]
// Configure task using named and a lambda
val myCopy = tasks.named<Copy>("myCopy") {
    from("resources")
    into("target")
}
myCopy {
    include("**/*.txt", "**/*.xml", "**/*.properties")
}
// end::configure[]
