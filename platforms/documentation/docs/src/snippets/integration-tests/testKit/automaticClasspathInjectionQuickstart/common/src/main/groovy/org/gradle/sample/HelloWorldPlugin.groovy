package org.gradle.sample

import org.gradle.api.Plugin
import org.gradle.api.Project

class HelloWorldPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.task('helloWorld') {
            doLast {
                println 'Hello world!'
            }
        }
    }
}
