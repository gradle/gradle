abstract class MyProjectLayoutTask
@Inject constructor(private var projectLayout: ProjectLayout) : DefaultTask() {

    @TaskAction
    fun doTaskAction() {
        val outputDirectory = projectLayout.projectDirectory
        println(outputDirectory)
    }
}

tasks.register("myInjectedProjectLayoutTask", MyProjectLayoutTask::class) {}
