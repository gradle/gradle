// tag::depended-upon-task-setup[]
abstract class SimplePrintingTask : DefaultTask() {
    @get:OutputFile
    abstract val messageFile: RegularFileProperty

    @get:OutputFile
    abstract val audienceFile: RegularFileProperty

    @TaskAction // <1>
    fun run() {
        messageFile.get().asFile.writeText("Hello")
        audienceFile.get().asFile.writeText("World")
    }
}

tasks.register<SimplePrintingTask>("helloWorld") { // <2>
    messageFile.set(layout.buildDirectory.file("message.txt"))
    audienceFile.set(layout.buildDirectory.file("audience.txt"))
}
// end::depended-upon-task-setup[]

// tag::avoid-this[]
abstract class SimpleTranslationTask : DefaultTask() {
    @get:InputFile
    abstract val messageFile: RegularFileProperty

    @get:OutputFile
    abstract val translatedFile: RegularFileProperty

    init {
        messageFile.convention(project.layout.buildDirectory.file("message.txt"))
        translatedFile.convention(project.layout.buildDirectory.file("translated.txt"))
    }

    @TaskAction // <1>
    fun run() {
        val message = messageFile.get().asFile.readText(Charsets.UTF_8)
        val translatedMessage = if (message == "Hello") "Bonjour" else "Unknown"

        logger.lifecycle("Translation: " + translatedMessage)
        translatedFile.get().asFile.writeText(translatedMessage)
    }
}

tasks.register<SimpleTranslationTask>("translateBad") {
    dependsOn(tasks.named("helloWorld")) // <2>
}
// end::avoid-this[]

// tag::do-this[]
tasks.register<SimpleTranslationTask>("translateGood") {
    inputs.file(tasks.named<SimplePrintingTask>("helloWorld").get().messageFile) // <1>
}
// end::do-this[]
