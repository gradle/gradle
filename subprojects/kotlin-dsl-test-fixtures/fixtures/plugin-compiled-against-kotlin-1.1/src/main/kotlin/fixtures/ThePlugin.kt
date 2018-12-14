package fixtures

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class ThePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.tasks.create("the-plugin-task", ThePluginTask::class.java)
    }
}

open class ThePluginTask : DefaultTask() {

    var from: String = "default from value"

    open fun transform(f: (String) -> String) = f(from)

    @TaskAction
    fun run() {
        println(transform { "it = $it" })
    }
}
