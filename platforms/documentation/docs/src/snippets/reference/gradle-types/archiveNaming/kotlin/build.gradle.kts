// tag::zip-task[]
plugins {
    base
}

version = "1.0"

tasks.register<Zip>("myZip") {
    from("somedir")
    val projectDir = layout.projectDirectory.asFile
    doLast {
        println(archiveFileName.get())
        println(destinationDirectory.get().asFile.relativeTo(projectDir))
        println(archiveFile.get().asFile.relativeTo(projectDir))
    }
}
// end::zip-task[]

// tag::zip-task-with-custom-base-name[]
tasks.register<Zip>("myCustomZip") {
    archiveBaseName = "customName"
    from("somedir")

    doLast {
        println(archiveFileName.get())
    }
}
// end::zip-task-with-custom-base-name[]
