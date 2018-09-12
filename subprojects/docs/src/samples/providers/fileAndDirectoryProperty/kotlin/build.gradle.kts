// A project extension
open class SourceGenerationExtension(objects: ObjectFactory) {
    // The directory to write the generated source files to
    val sourceDir: DirectoryProperty = objects.directoryProperty()

    // The configuration file to use for source generation
    val configFile: RegularFileProperty = objects.fileProperty()
}

// A task that generates a source file and writes the result to an output directory
open class GenerateSource @javax.inject.Inject constructor(objects: ObjectFactory): DefaultTask() {
    @InputFile
    val configFile: RegularFileProperty = objects.fileProperty()

    @OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    @TaskAction
    fun compile() {
        val inFile = configFile.get().asFile
        logger.quiet("configuration file = " + inFile)
        val dir = outputDir.get().asFile
        logger.quiet("output dir = " + dir)
        val srcFile = File(dir, "UsefulThing.java")
        srcFile.writeText("public class UsefulThing { }")
    }
}

// Create the project extension
val source = project.extensions.create("source", SourceGenerationExtension::class, project.objects)

// Create the source generation task
task<GenerateSource>("generate") {
    // Attach configuration from the project extension
    // Note that the values of the project extension have not been configured yet
    configFile.set(source.configFile)
    outputDir.set(source.sourceDir)
}

configure<SourceGenerationExtension> {
    // Configure the locations
    // Don't need to reconfigure the task's properties. These are automatically updated as the extension properties change
    sourceDir.set(project.layout.buildDirectory.dir("generated-source"))
    configFile.set(project.layout.projectDirectory.file("src/config.txt"))
}

// Change the build directory
// Don't need to reconfigure the extension or task properties. These are automatically updated as the build directory changes
buildDir = file("output")
