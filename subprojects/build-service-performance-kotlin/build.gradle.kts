import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener

plugins {
    kotlin("jvm")
}

tasks.register("simple", SimpleTask::class) {
    val properties = gradle.startParameter.systemPropertiesArgs
    creationWait.convention(properties.getOrDefault("creationWait", "10").toInt())
    numberOfServices.convention(properties.getOrDefault("numberOfServices", "5").toInt())
    config()
}

abstract class SimpleTask : DefaultTask() {
    @get:Option(option = "numberOfServices", description = "")
    @get:Input
    abstract val numberOfServices: Property<Int>

    @get:Option(option = "creationWait", description = "")
    @get:Input
    abstract val creationWait: Property<Int>

    @get:Inject
    abstract val buildServiceRegistry: BuildServiceRegistry

    @get:Inject
    abstract val buildEventsListenerRegistry: BuildEventsListenerRegistry

    fun config() {
        println("Registering ${numberOfServices.get()} services")
        for (index in 1..numberOfServices.get()) {
            createService(index)
        }
    }

    private fun createService(
        index: Int
    ): Provider<MyBuildService> {
        return buildServiceRegistry.registerIfAbsent("myBuildService$index", MyBuildService::class.java) {
            val serviceName = "service $index"
            parameters.name.set(serviceName)
            parameters.creationWait.set(creationWait.get())
            println(serviceName)
        }.also { buildService ->
            buildEventsListenerRegistry.onTaskCompletion(buildService)
        }
    }

    @TaskAction
    fun sayHello() {
        println("Using ${numberOfServices.get()} services")
        for (index in 1..numberOfServices.get()) {
            createService(index).get().doIt()
        }
    }
}

interface ServiceConfig: BuildServiceParameters {
    val name: Property<String>
    val creationWait: Property<Int>
}

abstract class MyBuildService: BuildService<ServiceConfig>,
    OperationCompletionListener, AutoCloseable {

    private val SERVICE_LOGGER = Logging.getLogger(MyBuildService::class.java)

    init {
        SERVICE_LOGGER.info("Service is instantiated: ${parameters.name.orNull}")
        val wait = parameters.creationWait.get()
        SERVICE_LOGGER.info("Waiting for $wait ms")
        if (wait > 0)
            Thread.sleep(wait.toLong())
    }

    override fun close() {
        SERVICE_LOGGER.info("Service is closed: ${parameters.name.orNull}")
    }

    override fun onFinish(event: FinishEvent?) {
        SERVICE_LOGGER.info("From ${parameters.name.orNull}: $event")
    }

    fun doIt() {
        SERVICE_LOGGER.info("Hello from ${parameters.name.get()}")
    }


}

dependencies {
    implementation(kotlin("stdlib"))
}
