// tag::do-this[]
abstract class MyTask : DefaultTask() {
    @TaskAction
    fun run() {
        heavyWork() // <1>
    }

    fun heavyWork() {
        println("Start heavy work")
        Thread.sleep(50)
        println("Finish heavy work")
    }
}

tasks.register<MyTask>("myTask")
// end::do-this[]
