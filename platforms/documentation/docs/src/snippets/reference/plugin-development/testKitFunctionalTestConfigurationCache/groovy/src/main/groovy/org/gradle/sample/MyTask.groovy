package org.gradle.sample

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class MyTask extends DefaultTask {

    @TaskAction
    void action() {}
}
