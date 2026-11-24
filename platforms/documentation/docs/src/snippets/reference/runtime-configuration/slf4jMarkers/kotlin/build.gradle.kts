// tag::generate-code-task[]
abstract class GenerateCode : DefaultTask() {
    @TaskAction
    fun execute() {
        CodeGenerator(Logging.LIFECYCLE).run()
    }
}
// end::generate-code-task[]

tasks.register<GenerateCode>("generateCode")
