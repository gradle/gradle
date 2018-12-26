open class Person {
    var name: String? = null
}

open class GreetingPluginExtension @javax.inject.Inject constructor(objectFactory: ObjectFactory) {
    var message: String? = null
    val greeter: Person = objectFactory.newInstance()

    fun greeter(action: Action<in Person>) {
        action.execute(greeter)
    }
}

class GreetingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create the 'greeting' extension
        val extension = project.extensions.create<GreetingPluginExtension>("greeting")
        project.task("hello") {
            doLast {
                println("${extension.message} from ${extension.greeter.name}")
            }
        }
    }
}

apply<GreetingPlugin>()

configure<GreetingPluginExtension> {
    message = "Hi"
    greeter {
        name = "Gradle"
    }
}
