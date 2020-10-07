import org.gradle.api.artifacts.transform.TransformParameters

// tag::artifact-transform-countloc[]
abstract class CountLoc : TransformAction<TransformParameters.None> {

    @get:Inject                                                         // <1>
    abstract val inputChanges: InputChanges

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override
    fun transform(outputs: TransformOutputs) {
        val outputDir = outputs.dir("${input.get().asFile.name}.loc")
        println("Running transform on ${input.get().asFile.name}, incremental: ${inputChanges.isIncremental}")
        inputChanges.getFileChanges(input).forEach { change ->          // <2>
            val changedFile = change.file
            if (change.fileType != FileType.FILE) {
                return@forEach
            }
            val outputLocation = outputDir.resolve("${change.normalizedPath}.loc")
            when (change.changeType) {
                ChangeType.ADDED, ChangeType.MODIFIED -> {

                    println("Processing file ${changedFile.name}")
                    outputLocation.parentFile.mkdirs()

                    outputLocation.writeText(changedFile.readLines().size.toString())
                }
                ChangeType.REMOVED -> {
                    println("Removing leftover output file ${outputLocation.name}")
                    outputLocation.delete()
                }
            }
        }
    }
}
// end::artifact-transform-countloc[]

val usage = Attribute.of("usage", String::class.java)
val artifactType = Attribute.of("artifactType", String::class.java)

dependencies {
    registerTransform(CountLoc::class) {
        from.attribute(artifactType, "java")
        to.attribute(artifactType, "loc")
    }
}

dependencies {
    attributesSchema {
        attribute(usage)
    }
}
configurations.create("compile") {
    attributes.attribute(usage, "api")
}
