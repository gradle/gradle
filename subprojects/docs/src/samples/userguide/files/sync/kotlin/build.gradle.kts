configurations.create("runtime")

// tag::copy-dependencies[]
task<Sync>("libs") {
    from(configurations["runtime"])
    into("$buildDir/libs")
}
// end::copy-dependencies[]
