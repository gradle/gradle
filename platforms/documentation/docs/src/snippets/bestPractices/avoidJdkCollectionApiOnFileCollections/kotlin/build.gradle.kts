plugins {
    `java-library`
}

// tag::bad-zipping-task[]
tasks.register<Zip>("badZippingTask") {
    val files = configurations.runtimeClasspath.get() + layout.projectDirectory.file("extra.txt") // <1>
    from(files)
}
// end::bad-zipping-task[]

// tag::good-zipping-task[]
tasks.register<Zip>("goodZippingTask") {
    val files = files(configurations.runtimeClasspath, layout.projectDirectory.file("extra.txt")) // <1>
    from(files)
}
// end::good-zipping-task[]
