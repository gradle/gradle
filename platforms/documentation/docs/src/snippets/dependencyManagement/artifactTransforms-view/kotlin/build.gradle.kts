plugins {
    java
}

repositories {
    mavenCentral()
}

// tag::artifact-views-with-custom-attribute[]
// The TestTransform class implements TransformAction,
// transforming input JAR files into text files with specific content
abstract class TestTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val outputFile = outputs.file("transformed-stub.txt")
        outputFile.writeText("Transformed from ${inputArtifact.get().asFile.name}")
    }
}

// The transform is registered to convert artifacts from the type "jar" to "stub"
dependencies {
    registerTransform(TestTransform::class.java) {
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "stub")
    }
}

// A consumer configuration is defined to resolve artifacts of type "stub"
val consumer = configurations.create("consumer") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false

    dependencies.add(project.dependencies.create("org.gradle.profiler:gradle-profiler:0.21.0"))

    attributes {
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "stub")
    }
}

// The testArtifact task queries and prints the attributes of resolved artifacts,
// showing the type conversion in action.
tasks.register("testArtifact") {
    val resolvedArtifacts = consumer.incoming.artifactView {
        attributes {
            attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "stub")
        }
    }.artifacts.resolvedArtifacts

    doLast {
        resolvedArtifacts.get().forEach  {
            println("Resolved artifact variant:")
            println("- ${it.variant}")
            println("Resolved artifact attributes:")
            println("- ${it.variant.attributes}")
            println("Resolved artifact type:")
            println("- ${it.variant.attributes.getAttribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE)}")
        }
    }
}
// end::artifact-views-with-custom-attribute[]
