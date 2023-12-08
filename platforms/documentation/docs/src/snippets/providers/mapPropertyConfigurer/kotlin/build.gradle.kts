abstract class Generator(): DefaultTask() {
    @get:Internal
    abstract val properties: MapProperty<String, Int>

    init {
        properties
            .unset()
            .convention(mapOf("a" to 1))
            .configure {
                putAll(mapOf("b" to 2, "c" to 3))
            }
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
    properties.configure {
        put("b", -2)
        // Values have not been configured yet
        putAll(providers.provider { mapOf("c" to c, "d" to c + 1) })
    }
}

// Configure the values. There is no need to reconfigure the task
c = 3
