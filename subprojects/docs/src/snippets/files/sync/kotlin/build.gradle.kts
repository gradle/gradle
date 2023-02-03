configurations.create("runtime")

// tag::copy-dependencies[]
tasks.register<Sync>("libs") {
    from(configurations["runtime"])
    into(layout.buildDirectory.dir("libs"))
}
// end::copy-dependencies[]
