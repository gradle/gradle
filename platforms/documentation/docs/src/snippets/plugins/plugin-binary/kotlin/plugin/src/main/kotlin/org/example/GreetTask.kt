package org.example

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GreetTask : DefaultTask() {
    @get:Input      // <1>
    abstract val message: Property<String>

    @get:OutputFile // <2>
    abstract val outputFile: RegularFileProperty

    @TaskAction     // <3>
    fun run() {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(message.get())
        logger.lifecycle("Wrote greeting to ${file}")
    }
}
