abstract class MyPropertyTask : DefaultTask() {
    @get:Input
    abstract val messageProperty: Property<String> // message property

    @TaskAction
    fun printMessage() {
        println(messageProperty.get())
    }
}

tasks.register<MyPropertyTask>("myPropertyTask") {
    messageProperty.convention("Hello, Gradle!")
}
