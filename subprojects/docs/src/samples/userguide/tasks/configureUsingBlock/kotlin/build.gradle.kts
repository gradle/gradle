// tag::declare-task[]
task<Copy>("myCopy")
// end::declare-task[]

// tag::configure[]
// Configure task using Kotlin delegated properties and a lambda
val myCopy by tasks.getting(Copy::class) {
    from("resources")
    into("target")
}
myCopy.include("**/*.txt", "**/*.xml", "**/*.properties")
// end::configure[]
