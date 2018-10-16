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
    @InputFiles
    val inputFiles: ListProperty<RegularFile> = project.objects.listProperty(RegularFile::class).empty()

    @TaskAction
    fun consume() {
        inputFiles.get().forEach { inputFile ->
            val input = inputFile.asFile
            val message = input.readText()
            logger.quiet("Read '${message}' from ${input}")
        }
    }
}

val producerOne by tasks.creating(Producer::class)
val producerTwo by tasks.creating(Producer::class)
val consumer by tasks.creating(Consumer::class)

// Connect the producer task outputs to the consumer task input
// Don't need to add task dependencies to the consumer task. These are automatically added
consumer.inputFiles.add(producerOne.outputFile)
consumer.inputFiles.add(producerTwo.outputFile)

// Set values for the producer tasks lazily
// Don't need to update the consumer.inputFiles property. This is automatically updated as producer.outputFile changes
producerOne.outputFile.set(layout.buildDirectory.file("one.txt"))
producerTwo.outputFile.set(layout.buildDirectory.file("two.txt"))

// Change the build directory.
// Don't need to update the task properties. These are automatically updated as the build directory changes
buildDir = file("output")
