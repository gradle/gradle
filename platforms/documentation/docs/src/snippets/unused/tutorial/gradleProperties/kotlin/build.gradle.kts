// tag::gradle-properties[]
// Using the API, provides a lazy Provider<String>
println(providers.gradleProperty("gradlePropertiesProp").get())

// Using the project API
val gradlePropertiesProp = project.property("gradlePropertiesProp") as String
println(gradlePropertiesProp)
// end::gradle-properties[]

abstract class PrintValue : DefaultTask() {
    @get:Input abstract val inputValue: Property<String>
    @TaskAction fun action() { println(inputValue.get()) }
}

// tag::gradle-properties-task-inputs[]
tasks.register<PrintValue>("printProperty") {
    // Using the API, provides a lazy Provider<String> wired to a task input
    inputValue = providers.gradleProperty("gradlePropertiesProp")
}
// end::gradle-properties-task-inputs[]
