// tag::all[]
// tag::task[]
abstract class GreetingToFileTask : DefaultTask() {

    @get:OutputFile
    abstract val destination: RegularFileProperty

    @TaskAction
    fun greet() {
        val file = getDestination().get().asFile
        file.parentFile.mkdirs()
        file.writeText("Hello!")
    }
}
// end::task[]

// tag::config[]
val greetingFile = layout.buildDirectory.file("greeting.txt")

tasks.register<GreetingToFileTask>("greet") {
    destination.set(greetingFile)
}

tasks.register("sayGreeting") {
    dependsOn("greet")
    doLast {
        val file = greetingFile.get().asFile
        println("${file.readText()} (file: ${file.name})")
    }
}

greetingFile.set(layout.buildDirectory.file("hello.txt"))
// end::config[]
// end::all[]
