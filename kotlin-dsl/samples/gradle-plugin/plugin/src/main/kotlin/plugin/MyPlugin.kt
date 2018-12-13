package plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy

import org.gradle.kotlin.dsl.*

class MyPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.run {

            tasks {
                register("myCopyTask", Copy::class) {
                    group = "sample"
                    from("build.gradle.kts")
                    into("build/copy")
                }
            }
        }
    }
}
