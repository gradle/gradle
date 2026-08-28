package org.gradle.sample

import org.gradle.api.Plugin
import org.gradle.api.Project

class MyPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.tasks.register("myTask", MyTask::class.java)
    }
}
