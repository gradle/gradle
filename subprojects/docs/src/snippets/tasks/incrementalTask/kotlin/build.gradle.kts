tasks.register("originalInputs") {
    val inputsDir = layout.projectDirectory.dir("inputs")
    outputs.dir(inputsDir)
    doLast {
        inputsDir.file("1.txt").asFile.writeText("Content for file 1.")
        inputsDir.file("2.txt").asFile.writeText("Content for file 2.")
        inputsDir.file("3.txt").asFile.writeText("Content for file 3.")
    }
}

// tag::updated-inputs[]
tasks.register("updateInputs") {
    val inputsDir = layout.projectDirectory.dir("inputs")
    outputs.dir(inputsDir)
    doLast {
        inputsDir.file("1.txt").asFile.writeText("Changed content for existing file 1.")
        inputsDir.file("4.txt").asFile.writeText("Content for new file 4.")
    }
}
// end::updated-inputs[]

// tag::removed-input[]
tasks.register<Delete>("removeInput") {
    delete("inputs/3.txt")
}
// end::removed-input[]

// tag::removed-output[]
tasks.register<Delete>("removeOutput") {
    delete(layout.buildDirectory.file("outputs/1.txt"))
}
// end::removed-output[]

// tag::reverse[]
tasks.register<IncrementalReverseTask>("incrementalReverse") {
    inputDir.set(file("inputs"))
    outputDir.set(layout.buildDirectory.dir("outputs"))
    inputProperty.set(project.findProperty("taskInputProperty") as String? ?: "original")
}
// end::reverse[]

tasks.named("incrementalReverse") { mustRunAfter("originalInputs", "updateInputs", "removeInput", "removeOutput") }

// tag::incremental-task[]
abstract class IncrementalReverseTask : DefaultTask() {
    @get:Incremental
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val inputProperty: Property<String>

    @TaskAction
    fun execute(inputChanges: InputChanges) {
        println(
            if (inputChanges.isIncremental) "Executing incrementally"
            else "Executing non-incrementally"
        )

        // tag::process-changed-inputs[]
        inputChanges.getFileChanges(inputDir).forEach { change ->
            if (change.fileType == FileType.DIRECTORY) return@forEach

            println("${change.changeType}: ${change.normalizedPath}")
            val targetFile = outputDir.file(change.normalizedPath).get().asFile
            if (change.changeType == ChangeType.REMOVED) {
                targetFile.delete()
            } else {
                targetFile.writeText(change.file.readText().reversed())
            }
        }
        // end::process-changed-inputs[]
    }
}
// end::incremental-task[]
