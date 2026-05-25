abstract class MyProviderFactoryTask
@Inject constructor(private var providerFactory: ProviderFactory) : DefaultTask() {

    @TaskAction
    fun doTaskAction() {
        val outputDirectory = providerFactory.provider { "build/my-file.txt" }
        println(outputDirectory.get())
    }
}

tasks.register("myInjectedProviderFactoryTask", MyProviderFactoryTask::class) {}
