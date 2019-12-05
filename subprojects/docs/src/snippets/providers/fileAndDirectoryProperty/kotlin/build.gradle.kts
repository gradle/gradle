// A task that generates a source file and writes the result to an output directory
open class GenerateSource @javax.inject.Inject constructor(objects: ObjectFactory): DefaultTask() {
    @InputFile
    val configFile: RegularFileProperty = objects.fileProperty()

    @OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    @TaskAction
    fun compile() {
        val inFile = configFile.get().asFile
        logger.quiet("configuration file = $inFile")
        val dir = outputDir.get().asFile
        logger.quiet("output dir = $dir")
        val className = inFile.readText().trim()
        val srcFile = File(dir, "${className}.java")
        srcFile.writeText("public class ${className} { }")
    }
}

// Create the source generation task
tasks.register<GenerateSource>("generate") {
    // Configure the locations, relative to the project and build directories
    configFile.set(project.layout.projectDirectory.file("src/config.txt"))
    outputDir.set(project.layout.buildDirectory.dir("generated-source"))
}

// Change the build directory
// Don't need to reconfigure the task properties. These are automatically updated as the build directory changes
buildDir = file("output")
