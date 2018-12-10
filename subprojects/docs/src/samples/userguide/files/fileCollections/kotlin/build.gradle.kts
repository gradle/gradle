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
    doLast {
        var srcDir: File? = null

        val collection = layout.files({
            srcDir?.listFiles()
        })

        srcDir = file("src")
        println("Contents of ${srcDir.name}")
        collection.map { relativePath(it) }.sorted().forEach { println(it) }

        srcDir = file("src2")
        println("Contents of ${srcDir.name}")
        collection.map { relativePath(it) }.sorted().forEach { println(it) }
    }
}
// end::closure[]

tasks.register("usage") {
    doLast {
        val collection = layout.files("src/file1.txt")

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
        val union = collection + layout.files("src/file2.txt")
        val difference = collection - layout.files("src/file2.txt")

        // end::usage[]
    }
}

tasks.register("filterTextFiles") {
    doLast {
        // tag::filtering-file-collections[]
        val textFiles: FileCollection = collection.filter { f: File ->
            f.name.endsWith(".txt")
        }
        // end::filtering-file-collections[]

        textFiles.map { relativePath(it) }.sorted().forEach { path: String ->
            println(path)
        }

        require(textFiles.files.map {it.name }.sorted() == listOf("file1.txt", "file2.txt", "file5.txt"))
    }
}
