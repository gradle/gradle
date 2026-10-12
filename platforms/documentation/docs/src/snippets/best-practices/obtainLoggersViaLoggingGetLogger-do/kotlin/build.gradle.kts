// tag::do-this[]
import org.gradle.api.logging.Logging

abstract class GreetingService : BuildService<BuildServiceParameters.None> {
    companion object {
        private val logger = Logging.getLogger(GreetingService::class.java)  // <1>
    }

    fun greet(name: String) {
        logger.lifecycle("Hello, {}!", name)
    }
}

abstract class GreetingPlugin : Plugin<Project> {
    companion object {
        private val logger = Logging.getLogger(GreetingPlugin::class.java)  // <2>
    }

    override fun apply(project: Project) {
        logger.lifecycle("Applying GreetingPlugin")

        val service =
            project.gradle.sharedServices.registerIfAbsent("greeting", GreetingService::class) {}

        project.tasks.register<PluginGreetingTask>("greet") {
            this.service = service
            usesService(service)
        }
    }
}

abstract class PluginGreetingTask : DefaultTask() {
    @get:Internal
    abstract val service: Property<GreetingService>

    @get:Input
    abstract val greeting: Property<String>

    @TaskAction
    fun run() {
        logger.lifecycle("Greeting from task: {}", greeting.get())  // <3>
        service.get().greet(greeting.get())
    }
}

apply<GreetingPlugin>()

tasks.named<PluginGreetingTask>("greet") {
    greeting = "world"
}
// end::do-this[]
