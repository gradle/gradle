// tag::convention-callsites[]

abstract class GreetingTask : DefaultTask() {
    // tag::convention-callsites-from-declaration[]
    // setting convention from declaration
    @Input
    val greeter = project.objects.property<String>().convention("person1")
    // end::convention-callsites-from-declaration[]

    // tag::convention-callsites-from-constructor[]
    // setting convention from constructor
    @get:Input
    abstract val guest: Property<String>

    init {
        guest.convention("person2")
    }
    // end::convention-callsites-from-constructor[]

    @TaskAction
    fun greet() {
        println("hello, ${guest.get()}, from ${greeter.get()}")
    }
}

// tag::convention-callsites-from-plugin[]
// setting convention when registering a task from plugin
class GreetingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.getTasks().register<GreetingTask>("hello") {
            greeter.convention("Greeter")
        }
    }
}
// end::convention-callsites-from-plugin[]

// tag::convention-callsites-from-buildscript[]
apply<GreetingPlugin>()

tasks.withType<GreetingTask>().configureEach {
    // setting convention from build script
    guest.convention("Guest")
}
// end::convention-callsites-from-buildscript[]

// end::convention-callsites[]



