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

// Wire the property from producer to consumer task
// Don't need to add a task dependency to the consumer task. This is automatically added
consumer.inputFile.set(producer.outputFile)

// Set values for the producer lazily
// Don't need to update the consumer.inputFile property. This is automatically updated as producer.outputFile changes
producer.outputFile.set(layout.buildDirectory.file("file.txt"))

// Change the build directory.
// Don't need to update producer.outputFile and consumer.inputFile. These are automatically updated as the build directory changes
buildDir = file("output")
