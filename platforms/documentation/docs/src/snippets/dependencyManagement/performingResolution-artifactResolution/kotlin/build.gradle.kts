plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
}

// tag::file-resolution-task[]
abstract class ResolveFiles : DefaultTask() {

    @get:InputFiles
    abstract val files: ConfigurableFileCollection

    @TaskAction
    fun print() {
        files.forEach {
            println(it.name)
        }
    }
}
// end::file-resolution-task[]

// tag::implicit-file-resolution[]
tasks.register<ResolveFiles>("resolveConfiguration") {
    files.from(configurations.runtimeClasspath)
}
// end::implicit-file-resolution[]

// tag::artifact-resolution-task[]
data class ArtifactDetails(
    val id: ComponentArtifactIdentifier,
    val variant: ResolvedVariantResult
)

abstract class ResolveArtifacts : DefaultTask() {

    @get:Input
    abstract val details: ListProperty<ArtifactDetails>

    @get:InputFiles
    abstract val files: ListProperty<File>

    fun from(artifacts: Provider<Set<ResolvedArtifactResult>>) {
        details.set(artifacts.map {
            it.map { artifact -> ArtifactDetails(artifact.id, artifact.variant) }
        })
        files.set(artifacts.map {
            it.map { artifact -> artifact.file }
        })
    }

    @TaskAction
    fun print() {
        assert(details.get().size == files.get().size)
        details.get().zip(files.get()).forEach { (details, file) ->
            println("${details.variant.displayName}:${file.name}")
        }
    }
}
// end::artifact-resolution-task[]

// tag::implicit-artifact-resolution[]
tasks.register<ResolveArtifacts>("resolveIncomingArtifacts") {
    from(configurations.runtimeClasspath.flatMap { it.incoming.artifacts.resolvedArtifacts })
}
// end::implicit-artifact-resolution[]

// tag::variant-reselection[]
tasks.register<ResolveFiles>("resolveSources") {
    files.from(configurations.runtimeClasspath.map {
        it.incoming.artifactView {
            withVariantReselection()
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME));
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION));
                attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL));
                attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES));
            }
        }.files
    })
}
// end::variant-reselection[]
