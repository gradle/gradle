plugins {
    base
}

version = "1.0"

// tag::base-plugin-config[]
base {
    archivesName = "gradle"
    distsDirectory = layout.buildDirectory.dir("custom-dist")
    libsDirectory = layout.buildDirectory.dir("custom-libs")
}
// end::base-plugin-config[]

val myZip = tasks.register<Zip>("myZip") {
    from("somedir")
}

val myOtherZip = tasks.register<Zip>("myOtherZip") {
    archiveAppendix = "wrapper"
    archiveClassifier = "src"
    from("somedir")
}

tasks.register("echoNames") {
    val projectNameString = project.name
    val archiveFileName = myZip.flatMap { it.archiveFileName }
    val myOtherArchiveFileName = myOtherZip.flatMap { it.archiveFileName }
    doLast {
        println("Project name: $projectNameString")
        println(archiveFileName.get())
        println(myOtherArchiveFileName.get())
    }
}
