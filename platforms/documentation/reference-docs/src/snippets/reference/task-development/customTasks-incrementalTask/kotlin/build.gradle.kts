// tag::incremental-task[]
public class IncrementalReverseTask : DefaultTask() {

    @get:Incremental
    @get:InputDirectory
    val inputDir: DirectoryProperty = project.objects.directoryProperty()

    @get:OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty()

    @get:Input
    val inputProperty: RegularFileProperty = project.objects.fileProperty() // File input property

    @TaskAction
    fun execute(inputs: InputChanges) { // InputChanges parameter
        val msg = if (inputs.isIncremental) "CHANGED inputs are out of date"
                  else "ALL inputs are out of date"
        println(msg)
    }
}
// end::incremental-task[]
