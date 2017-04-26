import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.PropertyState
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

apply {
    plugin(GreetingPlugin::class.java)
}

configure<GreetingPluginExtension> {
    message = "Hi from Gradle"
    outputFiles = files("$buildDir/a.txt", "$buildDir/b.txt")
}

open class GreetingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Add the 'greeting' extension object
        val greeting: GreetingPluginExtension = project.extensions.create(
            "greeting",
            GreetingPluginExtension::class.java,
            project)
        // Add a task that uses the configuration
        project.tasks {
            "hello"(Greeting::class) {
                provideMessage(greeting.messageProvider)
                outputFiles = greeting.outputFiles
            }
        }
    }
}

open class GreetingPluginExtension(val project: Project) {
    private val messageState: PropertyState<String>
    private val outputFileCollection: ConfigurableFileCollection

    var message: String
        get() = messageState.get()
        set(value) = messageState.set(value)
    val messageProvider: Provider<String>

    var outputFiles: ConfigurableFileCollection
        get() = outputFileCollection
        set(value) = outputFileCollection.setFrom(value)

    init {
        messageState = project.property(String::class.java)
        messageProvider = messageState
        outputFileCollection = project.files()
    }
}

open class Greeting : DefaultTask() {
    private val messageState: PropertyState<String> = project.property(String::class.java)
    private val outputFileCollection: ConfigurableFileCollection = project.files()

    @get:Input
    var message: String
        get() = messageState.get()
        set(value) = messageState.set(value)

    @get:OutputFiles
    var outputFiles: ConfigurableFileCollection
        get() = outputFileCollection
        set(value) = outputFileCollection.setFrom(value)

    fun provideMessage(message: Provider<String>) = messageState.set(message)

    @TaskAction
    fun printMessage() {
        logger.info("Writing message '$message' to files ${outputFiles.files}")
        outputFiles.forEach { it.writeText(message) }
    }
}
