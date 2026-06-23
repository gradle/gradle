// tag::not-supported[]
val dir = file("data")

fun listFiles(dir: File): List<String> =
    dir.listFiles { file: File -> file.isFile }.map { it.name }.sorted()

tasks.register("listFiles") {
    doLast {
        println(listFiles(dir))
    }
}
// end::not-supported[]

// tag::workaround[]
object Files { // <1>
    fun listFiles(dir: File): List<String> =
        dir.listFiles { file: File -> file.isFile }.map { it.name }.sorted()
}

tasks.register("listFilesFixed") {
    val dir = file("data") // <2>
    doLast {
        println(Files.listFiles(dir))
    }
}
// end::workaround[]
