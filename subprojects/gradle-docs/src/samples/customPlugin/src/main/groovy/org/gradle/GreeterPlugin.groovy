package org.gradle

import org.gradle.api.Project
import org.gradle.api.Plugin

class GreeterPlugin implements Plugin<Project> {

    void apply(Project target) {
        target.task('hello', type: Greeting)
    }
}