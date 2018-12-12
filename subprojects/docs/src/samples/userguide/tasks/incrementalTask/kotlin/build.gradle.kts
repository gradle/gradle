tasks.register("originalInputs") {
    doLast {
        file("inputs").mkdir()
        file("inputs/1.txt").writeText("Content for file 1.")
        file("inputs/2.txt").writeText("Content for file 2.")
        file("inputs/3.txt").writeText("Content for file 3.")
    }
}

// tag::updated-inputs[]
tasks.register("updateInputs") {
    doLast {
        file("inputs/1.txt").writeText("Changed content for existing file 1.")
        file("inputs/4.txt").writeText("Content for new file 4.")
    }
}
// end::updated-inputs[]

// tag::removed-input[]
tasks.register("removeInput") {
    doLast {
        file("inputs/3.txt").delete()
    }
}
// end::removed-input[]

// tag::removed-output[]
tasks.register("removeOutput") {
    doLast {
        file("$buildDir/outputs/1.txt").delete()
    }
}
// end::removed-output[]

// tag::reverse[]
tasks.register<IncrementalReverseTask>("incrementalReverse") {
    inputDir = file("inputs")
    outputDir = file("$buildDir/outputs")
    inputProperty = project.properties["taskInputProperty"] as String? ?: "original"
}
// end::reverse[]

// tag::incremental-task[]
open class IncrementalReverseTask : DefaultTask() {
    @InputDirectory
    lateinit var inputDir: File

    @OutputDirectory
    lateinit var outputDir: File

    @Input
    lateinit var inputProperty: String

    @TaskAction
    fun execute(inputs: IncrementalTaskInputs) {
        println(
            if (inputs.isIncremental) "CHANGED inputs considered out of date"
            else "ALL inputs considered out of date"
        )
        // tag::handle-non-incremental-inputs[]
        if (!inputs.isIncremental) {
            project.delete(outputDir.listFiles())
        }
        // end::handle-non-incremental-inputs[]

        // tag::out-of-date-inputs[]
        inputs.outOfDate {
            if (file.isDirectory) return@outOfDate

            println("out of date: ${file.name}")
            val targetFile = File(outputDir, file.name)
            targetFile.writeText(file.readText().reversed())
        }
        // end::out-of-date-inputs[]

        // tag::removed-inputs[]
        inputs.removed {
            if (file.isDirectory) return@removed

            println("removed: ${file.name}")
            val targetFile = File(outputDir, file.name)
            targetFile.delete()
        }
        // end::removed-inputs[]
    }
}
// end::incremental-task[]
