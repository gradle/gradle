// tag::configuration[]
// Using the Java API
println(System.getenv("ENVIRONMENTAL"))

// Using the Gradle API, provides a lazy Provider<String>
println(providers.environmentVariable("ENVIRONMENTAL").get())

// end::configuration[]

abstract class PrintValue : DefaultTask() {
    @get:Input abstract val inputValue: Property<String>
    @TaskAction fun action() { println(inputValue.get()) }
}

// tag::execution[]
tasks.register<PrintValue>("printValue") {
    // Using the Gradle API, provides a lazy Provider<String> wired to a task input
    inputValue = providers.environmentVariable("ENVIRONMENTAL")
}
// end::execution[]
