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

// tag::unpack-archive-subset-example[]
tasks.register<Copy>("unpackLibsDirectory") {
    from(zipTree("src/resources/thirdPartyResources.zip")) {
        // We are extracting only the files under `libs` directly inside `build/resources`
        include("libs/**")
        includeEmptyDirs = false
        eachFile {
            // We create a new relative path to file inside the archive to be used during the extraction.
            //   The new path drop the `libs` segment from the file path.
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
    }
    into("$buildDir/resources")
}
// end::unpack-archive-subset-example[]

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

