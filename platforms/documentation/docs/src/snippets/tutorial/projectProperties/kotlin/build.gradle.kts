// tag::configuration[]
// Querying the presence of a project property
if (hasProperty("myProjectProp")) {
    // Accessing the value, throws if not present
    println(property("myProjectProp"))
}

// Accessing the value of a project property, null if absent
println(findProperty("myProjectProp"))

// Accessing the Map<String, Any?> of project properties
println(properties["myProjectProp"])

// Using Kotlin delegated properties on `project`
val myProjectProp: String by project
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
