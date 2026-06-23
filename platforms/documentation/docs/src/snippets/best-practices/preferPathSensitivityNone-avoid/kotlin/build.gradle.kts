abstract class AnimalSearchTask : DefaultTask() {
    @get:Input
    abstract val find: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE) // <1>
    abstract val candidatesFile: RegularFileProperty

    @get:OutputFile
    abstract val resultsFile: RegularFileProperty

    @TaskAction
    fun search() {
        if (candidatesFile.get().getAsFile().readLines().contains(find.get())) {
            val msg = "Found a " + find.get() + "!"
            getLogger().lifecycle(msg)
            resultsFile.get().asFile.writeText(msg)
        }
    }
}

val useAlternateInput = providers.gradleProperty("useAlternateInput").isPresent()

val copyTask = tasks.register<Copy>("copy") {
    from(layout.projectDirectory.file("candidates.txt"))
    destinationDir = (if (useAlternateInput) { layout.buildDirectory.dir("alternateSearchInput") } else { layout.buildDirectory.dir("searchInput") }).get().asFile
}

tasks.register<AnimalSearchTask>("search") {
    find = "cat"
    candidatesFile.fileProvider(copyTask.map { File(it.destinationDir, "candidates.txt") })
    resultsFile = layout.buildDirectory.file("searchOutput/results.txt")
    dependsOn(copyTask)
}
