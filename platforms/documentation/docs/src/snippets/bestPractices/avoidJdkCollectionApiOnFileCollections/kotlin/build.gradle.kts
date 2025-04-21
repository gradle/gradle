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
    from(configurations.runtimeClasspath) // <1>
    from(layout.projectDirectory.file("extra.txt"))
}
// end::good-zipping-task[]
