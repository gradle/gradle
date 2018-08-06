import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*

open class HelloTask : DefaultTask() {

    init {
        group = "My"
        description = "Prints a description of ${project.name}."
    }

    @TaskAction
    fun run() {
        println("I'm ${project.name}")
    }
}

fun Project.declareHelloTask() =
    tasks.register<HelloTask>("hello")

val Project.hello: TaskProvider<HelloTask>
    get() = tasks.named<HelloTask>("hello")
