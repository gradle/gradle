// tag::do-this[]
abstract class MyTask : DefaultTask() {
    @TaskAction
    fun run() {
        logger.lifecycle(heavyWork()) // <1>
    }

    fun heavyWork(): String {
        logger.lifecycle("Start heavy work")
        Thread.sleep(5000)
        logger.lifecycle("Finish heavy work")
        return "Heavy computation result"
    }
}

tasks.register<MyTask>("myTask")
// end::do-this[]
