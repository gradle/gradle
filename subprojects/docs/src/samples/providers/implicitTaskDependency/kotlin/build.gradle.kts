/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


open class Producer : DefaultTask() {
    @get:OutputFile
    val outputFile: RegularFileProperty = newOutputFile()

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
    val inputFile: RegularFileProperty = newInputFile()

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
consumer.inputFile.set(producer.outputFile)

// Set values for the producer lazily
// Note that the consumer does not need to be changed again.
producer.outputFile.set(layout.buildDirectory.file("file.txt"))

// Change the base output directory.
// Note that this automatically changes producer.outputFile and consumer.inputFile
setBuildDir("output")
