// tag::zip-task[]
plugins {
    base
}

version = "1.0"

task<Zip>("myZip") {
    from("somedir")

    doLast {
        println(archiveName)
        println(relativePath(destinationDir))
        println(relativePath(archivePath))
    }
}
// end::zip-task[]

// tag::zip-task-with-custom-base-name[]
task<Zip>("myCustomZip") {
    baseName = "customName"
    from("somedir")

    doLast {
        println(archiveName)
    }
}
// end::zip-task-with-custom-base-name[]
