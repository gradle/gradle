version = "1.0"

// tag::do[]
abstract class ReleaseTask : DefaultTask() {
    @get:Input
    abstract val banner: Property<String> // <1>

    @TaskAction
    fun run() {
        println(banner.get()) // <2>
    }
}

tasks.register<ReleaseTask>("release") {
    banner = providers.provider { "=== Releasing build ${rootProject.version} ===" } // <3>
}
// end::do[]
