package org.gradle.sample

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class HelloWorld extends DefaultTask {
    @TaskAction
    void printMessage() {
        println 'Hello world!'
    }
}
