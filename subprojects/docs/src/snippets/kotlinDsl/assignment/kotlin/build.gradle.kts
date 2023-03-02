plugins {
    `base`
}

// tag::assignment[]
interface MyExtension {
    val input: Property<String>
    val output: RegularFileProperty
}

abstract class MyTask : DefaultTask() {
    @get:Input
    abstract val input: Property<String>
    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun execute() {
        output.get().asFile.writeText(input.get())
    }
}

val extension = extensions.create<MyExtension>("extension").apply {
    input.set("Hello Property") // <1>
    output.set(file("build/myTask/output.txt"))
    input = "Hello Property" // <2>
    ouput = file("build/myTask/output.txt")
}

tasks.register<MyTask>("myTask") {
    input = extension.input // <3>
    output = extension.output
}
// end::assignment[]
