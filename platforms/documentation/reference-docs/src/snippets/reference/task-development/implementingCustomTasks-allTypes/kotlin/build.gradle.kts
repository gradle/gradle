// tag::all-types[]
abstract class AllTypes : DefaultTask() {

    //inputs
    @get:Input
    abstract val inputString: Property<String>
    @get:InputFile
    abstract val inputFile: RegularFileProperty
    @get:InputDirectory
    abstract val inputDirectory: DirectoryProperty
    @get:InputFiles
    abstract val inputFileCollection: ConfigurableFileCollection
    @get:Classpath
    abstract val inputClasspath: ConfigurableFileCollection

    // outputs
    @get:OutputFile
    abstract val outputFile: RegularFileProperty
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty
    @get:OutputFiles
    abstract val outputFiles: ConfigurableFileCollection
    @get:OutputDirectories
    abstract val outputDirectories: ConfigurableFileCollection
}
// end::all-types[]
