abstract class Generator(): DefaultTask() {
    @get:Input
    abstract val properties: MapProperty<String, Int>

    init {
        properties.set(null as Map<String, Int>?)
        properties.value()
            .putAll(mapOf("a" to 1, "b" to 2))
    }

    @TaskAction
    fun generate() {
        properties.get().forEach { entry ->
            logger.quiet("${entry.key} = ${entry.value}")
        }
    }
}

// Some values to be configured later
var c = 0

tasks.register<Generator>("generate") {
    properties.value().put("b", -2)
    // Values have not been configured yet
    properties.value().putAll(providers.provider { mapOf("c" to c, "d" to c + 1) })
    properties.value().excludeAll("a", "d")
}

// Configure the values. There is no need to reconfigure the task
c = 3
