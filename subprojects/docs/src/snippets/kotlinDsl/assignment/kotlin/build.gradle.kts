plugins {
    `base`
}

// tag::assignment[]
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

tasks.register<MyTask>("myTask") {
    input.set("Hello Property") // <1>
    output.set(file("build/myTask/output.txt"))
    input = "Hello Property" // <2>
    ouput = file("build/myTask/output.txt")
    input = provider { "Hello Property" } // <3>
    output = provider { file("build/myTask/output.txt") }
}
// end::assignment[]
