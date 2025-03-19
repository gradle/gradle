package org.example

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "not worth caching")
abstract class MyTask : DefaultTask() {

    @TaskAction
    fun action() = Unit
}
