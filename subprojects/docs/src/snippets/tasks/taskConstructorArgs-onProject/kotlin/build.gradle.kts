import javax.inject.Inject

open class CustomTask @Inject constructor(
    private val message: String,
    private val number: Int
) : DefaultTask() {

    @TaskAction
    fun run() =
        println("$message $number")
}

// tag::on-project[]
task("myTask", "type" to CustomTask::class.java, "constructorArgs" to listOf("hello", 42))
// end::on-project[]
