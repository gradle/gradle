abstract class AnimalSearchTask : DefaultTask() {
    @get:Input
    abstract val find: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE) // <1>
    abstract val candidatesFile: RegularFileProperty

    @get:OutputFile
    abstract val resultsFile: RegularFileProperty

    @TaskAction
    fun check() {
        if (candidatesFile.get().getAsFile().readLines().contains(find.get())) {
            val msg = "Found a " + find.get() + "!"
            getLogger().lifecycle(msg)
            resultsFile.get().asFile.writeText(msg)
        }
    }
}

val originalCandidatesFile = layout.projectDirectory.dir("inputs").file("candidates.txt")
val alternateInputsDir = layout.buildDirectory.dir("alternateInputs")

tasks.register<AnimalSearchTask>("search") {
    find = "cat"
    if (project.hasProperty("useAlternateInput")) { // <2>
        candidatesFile = alternateInputsDir.map { it.file("candidates.txt") }
    } else {
        candidatesFile = originalCandidatesFile
    }
    resultsFile = layout.buildDirectory.file("search/results.txt")
}

tasks.register<Copy>("copy") { // <3>
    from(originalCandidatesFile)
    into(alternateInputsDir)
}
