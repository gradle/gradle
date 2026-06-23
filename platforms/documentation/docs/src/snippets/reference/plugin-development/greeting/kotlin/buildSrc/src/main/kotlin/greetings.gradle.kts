// tag::task[]
// tag::convention[]
// tag::create-extension[]
// tag::extension[]
// Create extension object
interface GreetingPluginExtension {
    val message: Property<String>
}
// end::extension[]

// Add the 'greeting' extension object to project
val extension = project.extensions.create<GreetingPluginExtension>("greeting")
// end::create-extension[]

// Set a default value for 'message'
extension.message.convention("Hello from Gradle")
// end::convention[]

// Create a greeting task
abstract class GreetingTask : DefaultTask() {
    @Input
    val message = project.objects.property<String>()

    @TaskAction
    fun greet() {
        println("Message: ${message.get()}")
    }
}

// Register the task and set the convention
tasks.register<GreetingTask>("hello") {
    message.convention(extension.message)
}
// end::task[]

// tag::update[]
// Where the<GreetingPluginExtension>() is equivalent to project.extensions.getByType(GreetingPluginExtension::class.java)
the<GreetingPluginExtension>().message.set("Hi from Gradle")
// end::update[]
the<GreetingPluginExtension>().message.set("Hello from Gradle")
