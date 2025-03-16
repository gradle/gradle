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
abstract class MyIntroTask : DefaultTask() {
    @get:Input
    abstract val configuration: Property<String>

    @TaskAction
    fun printConfiguration() {
        println("Configuration value: ${configuration.get()}")
    }
}

val configurationProvider: Provider<String> = project.provider { "Hello, Gradle!" }

tasks.register("myIntroTask", MyIntroTask::class) {
    configuration.set(configurationProvider)
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
    final val messageProvider: Provider<String> = project.providers.provider { "Hello, Gradle!" } // message provider

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
