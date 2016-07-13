package fixtures

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class ThePlugin() : Plugin<Project> {

    override fun apply(target: Project) {
        target.tasks.create("the-plugin-task", ThePluginTask::class.java)
    }
}

open class ThePluginTask : DefaultTask() {

    var from: String = "default from value"

    open fun configure(setup: (String) -> String) = setup(from)

    @TaskAction
    fun run() {
        println(configure { "it = $it" })
    }
}
