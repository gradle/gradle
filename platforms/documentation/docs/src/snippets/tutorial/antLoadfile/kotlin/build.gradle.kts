tasks.register("loadfile") {
    val resourceDirectory = file("./antLoadfileResources")
    doLast {
        val files = resourceDirectory.listFiles().sorted()
        files.forEach { file ->
            if (file.isFile) {
                ant.withGroovyBuilder {
                    "loadfile"("srcFile" to file, "property" to file.name)
                }
                println(" *** ${file.name} ***")
                println("${ant.properties[file.name]}")
            }
        }
    }
}
