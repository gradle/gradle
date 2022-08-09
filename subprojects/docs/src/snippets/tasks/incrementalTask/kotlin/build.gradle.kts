tasks.register("originalInputs") {
    outputs.dir("inputs")
    doLast {
        file("inputs").mkdir()
        file("inputs/1.txt").writeText("Content for file 1.")
        file("inputs/2.txt").writeText("Content for file 2.")
        file("inputs/3.txt").writeText("Content for file 3.")
    }
}

// tag::updated-inputs[]
tasks.register("updateInputs") {
    outputs.dir("inputs")
    doLast {
        file("inputs/1.txt").writeText("Changed content for existing file 1.")
        file("inputs/4.txt").writeText("Content for new file 4.")
    }
}
// end::updated-inputs[]

// tag::removed-input[]
tasks.register("removeInput") {
    outputs.dir("inputs")
    doLast {
        file("inputs/3.txt").delete()
    }
}
// end::removed-input[]

// tag::removed-output[]
tasks.register("removeOutput") {
    outputs.dir("$buildDir/outputs")
    doLast {
        file("$buildDir/outputs/1.txt").delete()
    }
}
// end::removed-output[]

// tag::reverse[]
tasks.register<IncrementalReverseTask>("incrementalReverse") {
    inputDir.set(file("inputs"))
    outputDir.set(file("$buildDir/outputs"))
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
