import javax.inject.Inject

// tag::inject-task-constructor[]
open class CustomTask @Inject constructor(
    private val message: String,
    private val number: Int
) : DefaultTask()
// end::inject-task-constructor[]

{
    @TaskAction
    fun run() =
        println("$message $number")
}

// tag::on-task-container[]
tasks.register<CustomTask>("myTask", "hello", 42)
// end::on-task-container[]
