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
    val inputFiles: ListProperty<RegularFile> = project.objects.listProperty(RegularFile::class)

    @TaskAction
    fun consume() {
        inputFiles.get().forEach { inputFile ->
            val input = inputFile.asFile
            val message = input.readText()
            logger.quiet("Read '${message}' from ${input}")
        }
    }
}

// Set values for the producer tasks lazily
// Don't need to update the consumer.inputFiles property. This is automatically updated as producer.outputFile changes
val producerOne = tasks.register<Producer>("producerOne") {
    outputFile.set(layout.buildDirectory.file("one.txt"))
}
val producerTwo = tasks.register<Producer>("producerTwo") {
    outputFile.set(layout.buildDirectory.file("two.txt"))
}
val consumer by tasks.register<Consumer>("consumer") {
    // Connect the producer task outputs to the consumer task input
    // Don't need to add task dependencies to the consumer task. These are automatically added
    consumer.inputFiles.add(producerOne.get().outputFile)
    consumer.inputFiles.add(producerTwo.get().outputFile)
}

// Change the build directory.
// Don't need to update the task properties. These are automatically updated as the build directory changes
buildDir = file("output")
