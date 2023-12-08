abstract class Executer() : DefaultTask() {
    @get:Internal
    abstract val options: ListProperty<String>

    init {
        options
            .unset()
            .convention(listOf("--debug", "--safe"))
            .configure {
                add("--verbose")
            }
    }

    @TaskAction
    fun execute() {
        options.get().forEach { option ->
            logger.quiet("${option}")
        }
    }
}
tasks.register<Executer>("executer") {
    options.configure {
        addAll("--native")
        excludeAll("--debug")
    }
}
