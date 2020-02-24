open class Producer : DefaultTask() {
    @OutputFile
    val outputFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun produce() {
        val message = "Hello, World!"
        val output = outputFile.get().asFile
        output.writeText( message)
        logger.quiet("Wrote '${message}' to ${output}")
    }
}

open class Consumer : DefaultTask() {
    @Input
    val message: Property<String> = project.objects.property(String::class)

    @TaskAction
    fun consume() {
        logger.quiet(message.get())
    }
}

val producer by tasks.registering(Producer::class) {
    // Set values for the producer lazily
    // Don't need to update the consumer.inputFile property. This is automatically updated as producer.outputFile changes
    outputFile.set(layout.buildDirectory.file("file.txt"))
}
val consumer by tasks.registering(Consumer::class) {
    // Connect the producer task output to the consumer task input
    // Don't need to add a task dependency to the consumer task. This is automatically added
    message.set(producer.map { it.outputFile.get().asFile.readText() })
}
