configurations.create("runtime")

// tag::copy-dependencies[]
tasks.create<Sync>("libs") {
    from(configurations["runtime"])
    into("${buildDir}/libs")
}
// end::copy-dependencies[]
