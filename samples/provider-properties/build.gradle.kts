apply<GreetingPlugin>()

configure<GreetingPluginExtension> {
    message.set("Hi from Gradle")
    outputFiles.from(
        project.layout.buildDirectory.file("a.txt"),
        project.layout.buildDirectory.file("b.txt"))
}

open class GreetingPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        // Add the 'greeting' extension object
        val greeting = project.extensions.create(
                "greeting",
                GreetingPluginExtension::class.java,
                project)

        // Add a task that uses the configuration
        project.tasks {
            "hello"(Greeting::class) {
                group = "Greeting"
                message.set(greeting.message)
                outputFiles.setFrom(greeting.outputFiles)
            }
        }
    }
}

open class GreetingPluginExtension(project: Project) {
    val message = project.objects.property<String>()
    val outputFiles: ConfigurableFileCollection = project.files()
}

open class Greeting : DefaultTask() {
    @get:Input
    val message = project.objects.property<String>()

    @get:OutputFiles
    val outputFiles: ConfigurableFileCollection = project.files()

    @TaskAction
    fun printMessage() {
        val message = message.get()
        val outputFiles = outputFiles.files
        logger.info("Writing message '$message' to files $outputFiles")
        outputFiles.forEach { it.writeText(message) }
    }
}
