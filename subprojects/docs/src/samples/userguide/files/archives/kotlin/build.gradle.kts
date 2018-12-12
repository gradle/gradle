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
    into("$buildDir/resources")
}
// end::unpack-archive-example[]

// tag::zip[]
tasks.register<Zip>("zip") {
    from("src/dist")
    into("libs") {
        from(configurations.runtime)
    }
}
// end::zip[]

// tag::tar[]
tasks.register<Tar>("tar") {
    from("src/dist")
    into("libs") {
        from(configurations.runtime)
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

