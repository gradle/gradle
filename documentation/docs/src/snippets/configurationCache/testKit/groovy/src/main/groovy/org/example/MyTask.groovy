package org.example

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class MyTask extends DefaultTask {

    @TaskAction
    void action() {}
}
