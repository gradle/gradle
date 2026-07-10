abstract class MyObjectFactoryTask
@Inject constructor(private var objectFactory: ObjectFactory) : DefaultTask() {

    @TaskAction
    fun doTaskAction() {
        val outputDirectory = objectFactory.directoryProperty()
        outputDirectory.convention(project.layout.projectDirectory)
        println(outputDirectory.get())
    }
}

tasks.register("myInjectedObjectFactoryTask", MyObjectFactoryTask::class) {}
