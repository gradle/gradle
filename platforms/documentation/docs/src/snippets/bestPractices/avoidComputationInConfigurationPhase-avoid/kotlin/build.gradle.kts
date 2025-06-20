// tag::avoid-this[]
abstract class MyTask : DefaultTask() {
    @get:Input
    lateinit var computationResult: String
    @TaskAction
    fun run() {
        logger.lifecycle(computationResult)
    }
}

fun heavyWork(): String {
    println("Start heavy work")
    Thread.sleep(5000)
    println("Finish heavy work")
    return "Heavy computation result"
}

tasks.register<MyTask>("myTask") {
    computationResult = heavyWork() // <1>
}
// end::avoid-this[]
