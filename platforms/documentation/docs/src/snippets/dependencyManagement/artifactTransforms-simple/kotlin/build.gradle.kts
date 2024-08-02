import org.gradle.api.artifacts.transform.TransformParameters

plugins {
    application
}

// tag::artifact-transform-imp[]
abstract class MyTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        val outputFile = outputs.file(inputFile.name.replace(".jar", "-transformed.jar"))
        // Perform transformation logic here
    }
}
// end::artifact-transform-imp[]

// tag::artifact-transform-use[]
val artifactType = Attribute.of("artifactType", String::class.java)

configurations.named("runtimeClasspath") {
    attributes {
        attribute(artifactType, "transformed-jar")
    }
}
// end::artifact-transform-use[]

// tag::artifact-transform-registration[]
dependencies {
    registerTransform(MyTransform::class) {
        from.attribute(artifactType, "jar")
        to.attribute(artifactType, "transformed-jar")
    }
}
// end::artifact-transform-registration[]


