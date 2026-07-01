// tag::avoid-this[]
import javax.inject.Inject
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

abstract class GreetingService : BuildService<BuildServiceParameters.None> {
    @get:Inject
    abstract val logger: Logger  // <1>

    fun greet(name: String) {
        logger.lifecycle("Hello, {}!", name)
    }
}

abstract class GreetingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.lifecycle("Applying GreetingPlugin")  // <2>

        val service =
            project.gradle.sharedServices.registerIfAbsent("greeting", GreetingService::class) {}

        project.tasks.register<PluginGreetingTask>("greet") {
            this.service = service
            usesService(service)
        }
    }
}

abstract class PluginGreetingTask : DefaultTask() {
    companion object {
        private val logger = Logging.getLogger(PluginGreetingTask::class.java)  // <3>
    }

    @get:Internal
    abstract val service: Property<GreetingService>

    @get:Input
    abstract val greeting: Property<String>

    @TaskAction
    fun run() {
        project.logger.lifecycle("Greeting from task: ${greeting.get()}")  // <4>
        service.get().greet(greeting.get())
    }
}

apply<GreetingPlugin>()

tasks.named<PluginGreetingTask>("greet") {
    greeting = "world"
}
// end::avoid-this[]
