// tag::zip-task[]
plugins {
    base
}

version = "1.0"

tasks.create<Zip>("myZip") {
    from("somedir")

    doLast {
        println(archiveName)
        println(relativePath(destinationDir))
        println(relativePath(archivePath))
    }
}
// end::zip-task[]

// tag::zip-task-with-custom-base-name[]
tasks.create<Zip>("myCustomZip") {
    baseName = "customName"
    from("somedir")

    doLast {
        println(archiveName)
    }
}
// end::zip-task-with-custom-base-name[]
