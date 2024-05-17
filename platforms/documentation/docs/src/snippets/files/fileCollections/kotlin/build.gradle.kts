import java.nio.file.Paths

// tag::simple-params[]
val collection: FileCollection = layout.files(
    "src/file1.txt",
    File("src/file2.txt"),
    listOf("src/file3.csv", "src/file4.csv"),
    Paths.get("src", "file5.txt")
)
// end::simple-params[]

file("src").mkdirs()
file("src/dir1").mkdirs()
file("src/file1.txt").mkdirs()
file("src2").mkdirs()
file("src2/dir1").mkdirs()
file("src2/dir2").mkdirs()

// tag::closure[]
tasks.register("list") {
    val projectDirectory = layout.projectDirectory
    doLast {
        var srcDir: File? = null

        val collection = projectDirectory.files({
            srcDir?.listFiles()
        })

        srcDir = projectDirectory.file("src").asFile
        println("Contents of ${srcDir.name}")
        collection.map { it.relativeTo(projectDirectory.asFile) }.sorted().forEach { println(it) }

        srcDir = projectDirectory.file("src2").asFile
        println("Contents of ${srcDir.name}")
        collection.map { it.relativeTo(projectDirectory.asFile) }.sorted().forEach { println(it) }
    }
}
// end::closure[]

tasks.register("usage") {
    val projectLayout = layout
    doLast {
        val collection = projectLayout.files("src/file1.txt")

        // tag::usage[]
        // Iterate over the files in the collection
        collection.forEach { file: File ->
            println(file.name)
        }

        // Convert the collection to various types
        val set: Set<File> = collection.files
        val list: List<File> = collection.toList()
        val path: String = collection.asPath
        val file: File = collection.singleFile

        // Add and subtract collections
        val union = collection + projectLayout.files("src/file2.txt")
        val difference = collection - projectLayout.files("src/file2.txt")

        // end::usage[]
    }
}

tasks.register("filterTextFiles") {
    // Copy collection property to a local variable for configuration cache support.
    val collection: FileCollection = collection
    val projectDirectory = layout.projectDirectory
    doLast {
        // tag::filtering-file-collections[]
        val textFiles: FileCollection = collection.filter { f: File ->
            f.name.endsWith(".txt")
        }
        // end::filtering-file-collections[]

        textFiles.map { it.relativeTo(projectDirectory.asFile).path }.sorted().forEach { path: String ->
            println(path)
        }

        require(textFiles.files.map { it.name }.sorted() == listOf("file1.txt", "file2.txt", "file5.txt"))
    }
}
