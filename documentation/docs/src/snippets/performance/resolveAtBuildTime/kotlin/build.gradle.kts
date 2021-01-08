plugins {
    id("java-library")
}

// tag::copy[]
tasks.register<Copy>("copyFiles") {
    into(layout.buildDirectory.dir("output"))
    from(configurations.compileClasspath)
    doFirst {
        println(">> Compilation deps: ${configurations.compileClasspath.get().files}")
    }
}
// end::copy[]
