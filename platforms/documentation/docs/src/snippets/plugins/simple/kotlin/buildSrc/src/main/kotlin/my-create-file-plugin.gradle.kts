abstract class CreateFileTask : DefaultTask() {
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

tasks.register<CreateFileTask>("createMyFileTaskInConventionPlugin") {
    group = "from my convention plugin"
    description = "Create myfile.txt in the current directory"
    fileText.set("HELLO FROM MY CONVENTION PLUGIN")
}
