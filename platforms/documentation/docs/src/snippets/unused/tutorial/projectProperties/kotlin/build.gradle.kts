// tag::configuration[]
// Recommended: Accessing the value of a project property via the providers API (type-safe, lazy)
// Note: does not look through the project hierarchy
println(providers.gradleProperty("myProjectProp").orNull)

// Accessing the value of a project property with hierarchical lookup, null if absent
println(findProperty("myProjectProp"))

// Querying the presence of a project property
if (hasProperty("myProjectProp")) {
    // Accessing the value, throws if not present
    println(property("myProjectProp"))
}

// Using the project API
val myProjectProp = project.property("myProjectProp") as String
println(myProjectProp)
// end::configuration[]

abstract class PrintValue : DefaultTask() {
    @get:Input abstract val inputValue: Property<String>
    @TaskAction fun action() { println(inputValue.get()) }
}

// tag::execution[]
tasks.register<PrintValue>("printValue") {
    // Eagerly accessing the value of a project property, set as a task input
    inputValue = project.property("myProjectProp").toString()
}
// end::execution[]
