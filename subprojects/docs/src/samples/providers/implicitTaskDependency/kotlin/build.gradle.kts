open class Producer : DefaultTask() {
    @get:OutputFile
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
    @get:InputFile
    val inputFile: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun consume() {
        val input = inputFile.get().asFile
        val message = input.readText()
        logger.quiet("Read '${message}' from ${input}")
    }
}

val producer by tasks.creating(Producer::class)
val consumer by tasks.creating(Consumer::class)

// Wire property from producer to consumer task
// Don't need to add a task dependency to the consumer task, this is automatically added
consumer.inputFile.set(producer.outputFile)

// Set values for the producer lazily
// Don't need to update the consumer.inputFile property, this is automatically updated
producer.outputFile.set(layout.buildDirectory.file("file.txt"))

// Change the base output directory.
// Don't need to update producer.outputFile and consumer.inputFile, these are automatically updated
setBuildDir("output")
