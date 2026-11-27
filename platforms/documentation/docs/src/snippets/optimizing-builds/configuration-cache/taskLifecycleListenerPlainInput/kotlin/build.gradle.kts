import javax.inject.Inject

// tag::plain-input-listener[]
// A task type from a plugin that you cannot change
abstract class StampBuildNumber : DefaultTask() {
    @get:Input
    var buildNumber: String = "local" // <1>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun stamp() {
        output.get().asFile.writeText(buildNumber)
        logger.lifecycle("Build number: $buildNumber")
    }
}

abstract class SetBuildNumber : TaskLifecycleListener<StampBuildNumber> {
    @get:Inject
    abstract val providers: ProviderFactory

    override fun beforeExecution(task: StampBuildNumber) { // <2>
        task.buildNumber = providers.gradleProperty("buildNumber").getOrElse("local") // <3>
    }
}

tasks.withType<StampBuildNumber>().addLifecycleListener(SetBuildNumber::class.java)
// end::plain-input-listener[]

tasks.register<StampBuildNumber>("stamp") {
    output = layout.buildDirectory.file("build-number.txt")
}
