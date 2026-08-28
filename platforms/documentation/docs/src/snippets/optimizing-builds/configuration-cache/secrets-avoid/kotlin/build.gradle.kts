// tag::avoid[]
abstract class PublishArtifactTask : DefaultTask() {
    @get:Input
    abstract val apiToken: Property<String>

    @TaskAction
    fun run() {
        // ... uses apiToken.get() to call the registry
    }
}

tasks.register<PublishArtifactTask>("publishArtifact") {
    // BAD: the literal token value is captured into the task field at configuration time
    apiToken = System.getenv("REGISTRY_TOKEN") ?: ""
}
// end::avoid[]
