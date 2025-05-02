plugins {
    id("java")
}

repositories {
    mavenCentral()
}

// tag::prop[]
val myNewProperty: String by rootProject.extra  // <1>
// end::prop[]

// tag::property[]
val myProperty: Property<String> = project.objects.property(String::class.java)

myProperty.set("Hello, Gradle!") // Set the value
println(myProperty.get())        // Access the value

// Using delegate syntax
val propValue: String by myProperty
println(propValue)

// Using lazy syntax
myProperty = "Hi, Gradle!" // Set the value
println(myProperty.get())  // Access the value
// end::property[]

// tag::provider[]
val versionProvider: Provider<String> = project.provider { "1.0.0" }

println(versionProvider.get()) // Access the value

// Chaining transformations
val majorVersion: Provider<String> = versionProvider.map { it.split(".")[0] }
println(majorVersion.get()) // Prints: "1"
// end::provider[]

// tag::named[]
val myTaskProvider: NamedDomainObjectProvider<Task> = tasks.named("build")

// Configuring the task
myTaskProvider.configure {
    doLast {
        println("Build task completed!")
    }
}

// Accessing the task
val myTask: Task = myTaskProvider.get()
// end::named[]
