package org.gradle.sample

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class MyTask : DefaultTask() {

    @TaskAction
    fun action() {}
}
