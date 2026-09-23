// tag::task-class[]
abstract class MyTask : DefaultTask() {

    @get:Input
    abstract val x: Property<Int>

    @TaskAction
    fun run() {
        println(x.get())
    }
}
// end::task-class[]

// tag::usage[]
tasks.register<MyTask>("myTask") {
    x.set(123)
}
// end::usage[]
