// tag::do[]
abstract class PublishArtifactTask : DefaultTask() {
    @get:Input
    abstract val apiToken: Property<String>

    @TaskAction
    fun run() {
        // ... uses apiToken.get() to call the registry
    }
}

tasks.register<PublishArtifactTask>("publishArtifact") {
    apiToken = providers.environmentVariable("REGISTRY_TOKEN").orElse("") // <1> <2>
}
// end::do[]
