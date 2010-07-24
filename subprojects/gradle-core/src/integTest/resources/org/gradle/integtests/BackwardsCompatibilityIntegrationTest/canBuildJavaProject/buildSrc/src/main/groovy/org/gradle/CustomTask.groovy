package org.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class CustomTask extends DefaultTask {
    @TaskAction
    def go() {
    }
}
