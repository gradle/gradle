// tag::move-rename[]
tasks.register("moveFile") {
    doLast {
        val sourceFile = file("source.txt")
        val destFile = file("destination/new_name.txt")

        if (sourceFile.renameTo(destFile)) {
            println("File moved successfully.")
        }
    }
}
// end::move-rename[]

// tag::move-copy[]
tasks.register<Copy>("moveFileCopy") {
    from("source.txt")
    into("destination")
    rename { "new_name.txt" }
}
// end::move-copy[]

// tag::create-dirs[]
tasks.register("createDirs") {
    doLast {
        mkdir("src/main/resources")
        mkdir(file("build/generated"))

        // Create multiple dirs
        mkdir(files("src/main/resources", "src/test/resources"))

        // Check dir existence
        val dir = file("src/main/resources")
        if (!dir.exists()) {
            mkdir(dir)
        }
    }
}
// end::create-dirs[]
