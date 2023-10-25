abstract class Executer() : DefaultTask() {
    @get:Internal
    abstract val options: ListProperty<String>

    init {
        options.set(null as List<String>?)
        options.value().add("--safe")
        options.value().add("--debug")
    }

    @TaskAction
    fun execute() {
        options.get().forEach { option ->
            logger.quiet("${option}")
        }
    }
}
tasks.register<Executer>("executer") {
    options.value().addAll("--native")
    options.value().excludeAll("--debug")
}
