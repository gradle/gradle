import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.script.lang.kotlin.*

open class HelloTask : DefaultTask() {

    override fun getDescription() =
        "Prints a description of ${project.name}."

    @TaskAction
    fun run() {
        println("I'm ${project.name}")
    }
}

fun Project.declareHelloTask() =
    task<HelloTask>("hello")

val Project.hello: HelloTask
    get() = tasks.getByName("hello") as HelloTask

