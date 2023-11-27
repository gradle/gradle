// tag::zip[]
// tag::tar[]
plugins {
    java
}

// end::tar[]
// end::zip[]

// tag::unpack-archive-example[]
tasks.register<Copy>("unpackFiles") {
    from(zipTree("src/resources/thirdPartyResources.zip"))
    into(layout.buildDirectory.dir("resources"))
}
// end::unpack-archive-example[]

// tag::unpack-archive-subset-example[]
tasks.register<Copy>("unpackLibsDirectory") {
    from(zipTree("src/resources/thirdPartyResources.zip")) {
        include("libs/**")  // <1>
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())  // <2>
        }
        includeEmptyDirs = false  // <3>
    }
    into(layout.buildDirectory.dir("resources"))
}
// end::unpack-archive-subset-example[]

// tag::zip[]
tasks.register<Zip>("zip") {
    from("src/dist")
    into("libs") {
        from(configurations.runtimeClasspath)
    }
}
// end::zip[]

// tag::tar[]
tasks.register<Tar>("tar") {
    from("src/dist")
    into("libs") {
        from(configurations.runtimeClasspath)
    }
}
// end::tar[]

// TODO why can't we use properties instead of setters here?
// when using them, we get "Cannot access 'preserveFileTimestamps': it is private in 'AbstractArchiveTask'"
// tag::reproducible[]
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
// end::reproducible[]

