// tag::set-prop[]
// Setting a property
val simpleMessageProperty: Property<String> = project.objects.property(String::class)
simpleMessageProperty.set("Hello, World from a Property!")
// Accessing a property
println(simpleMessageProperty.get())
// end::set-prop[]

// tag::set-prov[]
// Setting a provider
val simpleMessageProvider: Provider<String> = project.providers.provider { "Hello, World from a Provider!" }
// Accessing a provider
println(simpleMessageProvider.get())
// end::set-prov[]

// tag::introduction[]
// Define a custom task that prints a message
abstract class CustomTask : DefaultTask() {
    init {
        // Configure the task to print a message when executed
        doLast {
            println("Executing custom task")
        }
    }
}

// Define a custom plugin that adds the custom task to the project
abstract class CustomPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create a lazy property (provider) for the custom task
        val customTaskProvider: Provider<CustomTask> = project.tasks.register("customTask", CustomTask::class)

        // Configure a task to depend on the lazy property
        project.tasks.register("dependentTask") {
            dependsOn(customTaskProvider)
            doLast {
                println("Dependent task executed after custom task")
            }
        }
    }
}
// end::introduction[]

// Property
// tag::prop-managed[]
abstract class MyPropertyTask : DefaultTask() {
    @get:Input
    abstract val messageProperty: Property<String> // message property

    @TaskAction
    fun printMessage() {
        println(messageProperty.get())
    }
}

tasks.register<MyPropertyTask>("myPropertyTask") {
    messageProperty.set("Hello, Gradle!")
}
// end::prop-managed[]

// Provider
// tag::prov-managed[]
abstract class MyProviderTask : DefaultTask() {
    private val messageProvider: Provider<String> = project.providers.provider { "Hello, Gradle!" } // message provider

    @TaskAction
    fun printMessage() {
        println(messageProvider.get())
    }
}

tasks.register<MyProviderTask>("MyProviderTask") {

}
// end::prov-managed[]

// Named managed type
// tag::named[]
interface MyNamedType {
    val name: String
}

class MyNamedTypeImpl(override val name: String) : MyNamedType

class MyPluginExtension(project: Project) {
    val myNamedContainer: NamedDomainObjectContainer<MyNamedType> =
        project.container(MyNamedType::class.java) { name ->
            project.objects.newInstance(MyNamedTypeImpl::class.java, name)
        }
}
// end::named[]
