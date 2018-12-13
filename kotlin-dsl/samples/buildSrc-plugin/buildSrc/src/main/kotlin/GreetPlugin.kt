import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.*


class GreetPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        tasks {
            register("greet") {
                group = "sample"
                description = "Prints a description of ${project.name}."
                doLast {
                    println("I'm ${project.name}.")
                }
            }
        }
    }
}
