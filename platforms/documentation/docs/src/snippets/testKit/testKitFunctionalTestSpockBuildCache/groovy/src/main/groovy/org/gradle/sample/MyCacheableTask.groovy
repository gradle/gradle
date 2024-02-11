package org.gradle.sample

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
class MyCacheableTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    File inputFile
    @OutputFile
    File outputFile

    @TaskAction
    void doSomething() {
        outputFile.text = inputFile.text
    }
}
