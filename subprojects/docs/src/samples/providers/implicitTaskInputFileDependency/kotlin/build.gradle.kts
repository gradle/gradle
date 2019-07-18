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
    @InputFile
    val inputFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun consume() {
        val input = inputFile.get().asFile
        val message = input.readText()
        logger.quiet("Read '${message}' from ${input}")
    }
}

val producer by tasks.registering(Producer::class)
val consumer by tasks.registering(Consumer::class)

consumer.configure {
    // Connect the producer task output to the consumer task input
    // Don't need to add a task dependency to the consumer task. This is automatically added
    inputFile.set(producer.flatMap { it.outputFile })
}

producer.configure {
    // Set values for the producer lazily
    // Don't need to update the consumer.inputFile property. This is automatically updated as producer.outputFile changes
    outputFile.set(layout.buildDirectory.file("file.txt"))
}

// Change the build directory.
// Don't need to update producer.outputFile and consumer.inputFile. These are automatically updated as the build directory changes
buildDir = file("output")
