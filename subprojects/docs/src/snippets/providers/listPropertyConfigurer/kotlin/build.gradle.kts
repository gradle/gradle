abstract class Executer() : DefaultTask() {
    @get:Internal
    abstract val options: ListProperty<String>

    init {
        options.set(null as List<String>?)
        options.convention(emptyList())
        options.actualValue.add("--safe")
        options.actualValue.add("--debug")
    }

    @TaskAction
    fun execute() {
        options.get().forEach { option ->
            logger.quiet("${option}")
        }
    }
}
tasks.register<Executer>("executer") {
    options.actualValue.add("--native")
    options.actualValue.remove("--debug")
}
