import java.time.Instant

interface MyExtension {
    val firstName: Property<String>
    val lastName: Property<String>
}

var greeter = "Hello"

@CacheableTask // <1>
abstract class MyTask: DefaultTask() {
    @get:Input
    abstract val firstName: Property<String>
    @get:Input
    abstract val lastName: Property<String>
    @get:Input
    abstract val greeting: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private final val today = Instant.now() // <2>

    @TaskAction
    fun run() {
        val output = outputFile.asFile.get()
        val result = "${greeting.get()}, ${firstName.get()} ${lastName.get()}, it's currently\n$today"
        println(result)
        output.writeText(result)
    }
}

abstract class MyPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("myExtension", MyExtension::class.java)

        project.tasks.register<MyTask>("task1") {
            outputFile.convention(project.layout.buildDirectory.file("output1.txt"))
        }

        project.tasks.register<MyTask>("task2") {
            outputFile.convention(project.layout.buildDirectory.file("output2.txt"))
        }

        project.tasks.withType<MyTask>().configureEach {
            group = "Custom Tasks"
            firstName.convention(extension.firstName)
            lastName.convention(extension.firstName) // <3>
            greeting.convention("Hi")
        }
    }
}

apply<MyPlugin>()

configure<MyExtension> {
    firstName = "John"
    lastName = "Smith"
}

tasks.named<MyTask>("task2") {
    greeter = "Bonjour" // <4>
}
