apply<GreetingPlugin>()

configure<GreetingPluginExtension> {
    message = "Hi from Gradle"
    outputFiles = files("$buildDir/a.txt", "$buildDir/b.txt")
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
                provideMessage(greeting.messageProvider)
                outputFiles = greeting.outputFiles
            }
        }
    }
}

open class GreetingPluginExtension(project: Project) {

    private
    val messageProperty = project.objects.property<String>()

    var message by messageProperty

    val messageProvider: Provider<String> get() = messageProperty

    var outputFiles by project.files()
}

open class Greeting : DefaultTask() {

    private
    val messageProperty = project.objects.property<String>()

    @get:Input
    var message by messageProperty

    @get:OutputFiles
    var outputFiles by project.files()

    fun provideMessage(message: Provider<String>) = messageProperty.set(message)

    @TaskAction
    fun printMessage() {
        logger.info("Writing message '$message' to files ${outputFiles.files}")
        outputFiles.forEach { it.writeText(message) }
    }
}
