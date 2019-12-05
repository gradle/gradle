// tag::zip-task[]
plugins {
    base
}

version = "1.0"

tasks.register<Zip>("myZip") {
    from("somedir")

    doLast {
        println(archiveFileName.get())
        println(relativePath(destinationDirectory))
        println(relativePath(archiveFile))
    }
}
// end::zip-task[]

// tag::zip-task-with-custom-base-name[]
tasks.register<Zip>("myCustomZip") {
    archiveBaseName.set("customName")
    from("somedir")

    doLast {
        println(archiveFileName.get())
    }
}
// end::zip-task-with-custom-base-name[]
