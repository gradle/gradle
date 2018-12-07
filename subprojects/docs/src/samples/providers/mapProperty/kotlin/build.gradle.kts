open class Generator: DefaultTask() {
    @Input
    val properties: MapProperty<String, Int> = project.objects.mapProperty(String::class, Int::class)

    @TaskAction
    fun generate() {
        properties.get().forEach { entry ->
            logger.quiet("${entry.key} = ${entry.value}")
        }
    }
}

// Some values to be configured later
var b = 0
var c = 0

val generate by tasks.creating(Generator::class)

generate.properties.put("a", 1)
// Values have not been configured yet
generate.properties.put("b", providers.provider { b })
generate.properties.putAll(providers.provider { mapOf("c" to c, "d" to c + 1) })

// Configure the values. There is no need to reconfigure the task
b = 2
c = 3
