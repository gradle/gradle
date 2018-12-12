// tag::declare-task[]
tasks.register<Copy>("myCopy")
// end::declare-task[]

// tag::configure[]
val myCopy by tasks.existing(Copy::class) {
    from("resources")
    into("target")
}
myCopy { include("**/*.txt", "**/*.xml", "**/*.properties") }
// end::configure[]