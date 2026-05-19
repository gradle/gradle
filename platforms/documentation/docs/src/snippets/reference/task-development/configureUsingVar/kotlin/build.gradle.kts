tasks.register<Copy>("myCopy")

// tag::configure[]
tasks.named<Copy>("myCopy") {
    from("resources")
    into("target")
    include("**/*.txt", "**/*.xml", "**/*.properties")
}
// end::configure[]
