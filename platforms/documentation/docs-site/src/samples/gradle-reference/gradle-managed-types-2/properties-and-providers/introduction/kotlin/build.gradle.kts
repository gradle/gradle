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
    configuration = configurationProvider
}
