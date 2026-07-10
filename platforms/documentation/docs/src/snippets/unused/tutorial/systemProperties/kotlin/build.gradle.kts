// tag::system-properties[]
// Using the Java API
println(System.getProperty("system"))

// Using the Gradle API, provides a lazy Provider<String>
println(providers.systemProperty("system").get())
// end::system-properties[]

abstract class PrintValue : DefaultTask() {
    @get:Input abstract val inputValue: Property<String>
    @TaskAction fun action() { println(inputValue.get()) }
}

// tag::system-properties-task-inputs[]
tasks.register<PrintValue>("printProperty") {
    // Using the Gradle API, provides a lazy Provider<String> wired to a task input
    inputValue = providers.systemProperty("system")
}
// end::system-properties-task-inputs[]
