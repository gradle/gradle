// tag::all[]
// tag::task[]
abstract class GreetingToFileTask : DefaultTask() {

    @get:OutputFile
    abstract val destination: RegularFileProperty

    @TaskAction
    fun greet() {
        val file = destination.get().asFile
        file.parentFile.mkdirs()
        file.writeText("Hello!")
    }
}
// end::task[]

// tag::config[]
val greetingFile = objects.fileProperty()

tasks.register<GreetingToFileTask>("greet") {
    destination.set(greetingFile)
}

tasks.register("sayGreeting") {
    dependsOn("greet")
    val greetingFile = greetingFile
    doLast {
        val file = greetingFile.get().asFile
        println("${file.readText()} (file: ${file.name})")
    }
}

greetingFile.set(layout.buildDirectory.file("hello.txt"))
// end::config[]
// end::all[]
