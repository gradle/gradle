package org.example

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GreetTask extends DefaultTask {
    @Input          // <1>
    abstract Property<String> getMessage()

    @OutputFile     // <2>
    abstract RegularFileProperty getOutputFile()

    @TaskAction     // <3>
    void run() {
        def file = getOutputFile().get().asFile
        file.parentFile.mkdirs()
        file.write(getMessage().get())
        logger.lifecycle("Wrote greeting to ${file}")
    }
}
