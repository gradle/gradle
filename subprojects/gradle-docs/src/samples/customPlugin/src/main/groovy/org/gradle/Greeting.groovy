package org.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class Greeting extends DefaultTask {
    @TaskAction
    def greet() {
        println 'hello from Gradle'
    }
}