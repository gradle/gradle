// tag::do[]
interface VerifyWorkParameters : WorkParameters { // <3>
    val artifact: RegularFileProperty
}

abstract class VerifyWorkAction : WorkAction<VerifyWorkParameters> { // <1>
    override fun execute() {
        com.example.signing.SignatureVerifier.verify(parameters.artifact.get().asFile)
    }
}

abstract class VerifySignatureTask : DefaultTask() {
    @get:InputFile
    abstract val artifact: RegularFileProperty

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun run() {
        val workQueue = workerExecutor.processIsolation() // <2>
        workQueue.submit(VerifyWorkAction::class) {
            artifact = this@VerifySignatureTask.artifact
        }
    }
}
// end::do[]

tasks.register<VerifySignatureTask>("verify") {
    artifact = layout.projectDirectory.file("sample.txt")
}
