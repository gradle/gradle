tasks.register("generateVersion") {
    val versionFile = layout.buildDirectory.file("version.txt")
    outputs.file(versionFile)
    doLast {
        versionFile.get().asFile.writeText("1.0")
    }
}

// Reads the file that generateVersion writes, without declaring a dependency on it
tasks.register<Zip>("packageApp") {
    from(layout.buildDirectory.file("version.txt"))
    destinationDirectory = layout.buildDirectory.dir("dist")
    archiveFileName = "app.zip"
}
