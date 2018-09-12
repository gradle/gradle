class GreetingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<GreetingPluginExtension>("greeting", project)
        project.tasks.create<Greeting>("hello") {
            message = extension.message
            outputFiles = extension.outputFiles
        }
    }
}

open class GreetingPluginExtension(private val messageProp: Property<String>,
                                   private val configurableOutputFiles: ConfigurableFileCollection) {

    var message by messageProp
    var outputFiles: FileCollection by configurableOutputFiles

    constructor(project: Project): this(project.objects.property<String>(), project.layout.configurableFiles()) {
        message = "Hello from GreetingPlugin"
    }
}

open class Greeting : DefaultTask() {
    private val configurableOutputFiles: ConfigurableFileCollection = project.layout.configurableFiles()

    var message by project.objects.property<String>()
    var outputFiles: FileCollection by configurableOutputFiles

    @TaskAction
    fun printMessage() {
        outputFiles.forEach {
            logger.quiet("Writing message 'Hi from Gradle' to file")
            it.writeText(message)
        }
    }
}

apply<GreetingPlugin>()

configure<GreetingPluginExtension> {
    message = "Hi from Gradle"
    outputFiles = layout.files("a.txt", "b.txt")
}
