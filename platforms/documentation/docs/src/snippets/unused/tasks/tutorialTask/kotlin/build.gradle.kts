// tag::hello[]
// Extend the DefaultTask class to create a HelloTask class
abstract class HelloTask : DefaultTask() {
    @TaskAction
    fun hello() {
        println("hello from HelloTask")
    }
}

// Register the hello Task with type HelloTask
tasks.register<HelloTask>("hello") {
    group = "Custom tasks"
    description = "A lovely greeting task."
}
// end::hello[]

// tag::registers[]
// tag::file[]
abstract class CreateFileTask : DefaultTask() {
    @TaskAction
    fun action() {
        val file = File("myfile.txt")
        file.createNewFile()
        file.writeText("HELLO FROM MY TASK")
    }
}
// end::file[]

tasks.register<CreateFileTask>("createFileTask") {
    group = "custom"
    description = "Create myfile.txt in the current directory"
}
// end::registers[]

/*
// tag::register[]
abstract class CreateFileTask : DefaultTask() {
    @TaskAction
    fun action() {
        val file = File("myfile.txt")
        file.createNewFile()
        file.writeText("HELLO FROM MY TASK")
    }
}

tasks.register<CreateFileTask>("createFileTask")
// end::register[]
 */

// tag::default[]
// tag::class[]
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
// end::default[]

tasks.register<CreateAFileTask>("createAFileTask") {
    group = "custom"
    description = "Create myfile.txt in the current directory"
    fileText.convention("HELLO FROM THE CREATE FILE TASK METHOD") // Set convention
}

tasks.named<CreateAFileTask>("createAFileTask") {
    fileText.set("HELLO FROM THE NAMED METHOD") // Override with custom message
}
// end::class[]
