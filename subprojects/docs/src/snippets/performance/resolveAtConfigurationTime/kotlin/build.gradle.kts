plugins {
    id("java-library")
}

// tag::copy[]
tasks.register<Copy>("copyFiles") {
    println(">> Compilation deps: ${configurations.compileClasspath.get().files}")
    into(layout.buildDirectory.dir("output"))
    from(configurations.compileClasspath)
}
// end::copy[]
