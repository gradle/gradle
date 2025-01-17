plugins {
    java
}

repositories {
    mavenCentral()
}

// tag::artifact-views[]
abstract class TestTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val outputFile = outputs.file("transformed-stub.txt")
        outputFile.writeText("Transformed from ${inputArtifact.get().asFile.name}")
    }
}

// Register the transform
dependencies {
    registerTransform(TestTransform::class.java) {
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "stub")
    }
}

// Define a configuration to resolve the transformed artifact
val consumer = configurations.create("consumer") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false

    dependencies.add(project.dependencies.create("org.gradle.profiler:gradle-profiler:0.21.0"))

    attributes {
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "stub")
    }
}

// Task to resolve and display transformed artifact information
tasks.register("testArtifact") {
    val resolvedArtifacts = consumer.incoming.artifacts.artifactFiles

    doLast {
        println("Transformed artifacts:")
        resolvedArtifacts.files.forEach {
            println("- ${it.absolutePath}")
        }
    }
}
// end::artifact-views[]
