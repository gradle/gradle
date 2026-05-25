abstract class CreateAFileTask : DefaultTask() {
    @get:Input
    abstract val fileText: Property<String>

    @Input
    val fileName = "myfile.txt"

    @OutputFile
    val myFile: File = File(fileName)

    @TaskAction
    fun action() {
        myFile.createNewFile()
        myFile.writeText(fileText.get())
    }
}
