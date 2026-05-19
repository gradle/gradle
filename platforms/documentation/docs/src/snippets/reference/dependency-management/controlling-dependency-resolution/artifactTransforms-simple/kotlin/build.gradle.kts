import org.gradle.api.artifacts.transform.TransformParameters

plugins {
    application
}

dependencies {
    implementation("com.google.guava:guava:33.2.1-jre")
}

repositories {
    mavenCentral()
}

// tag::artifact-transform-imp[]
abstract class MyTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        val outputFile = outputs.file(inputFile.name.replace(".jar", "-transformed.jar"))
        // Perform transformation logic here
        inputFile.copyTo(outputFile, overwrite = true)
    }
}
// end::artifact-transform-imp[]

// tag::artifact-transform-use[]
configurations.named("runtimeClasspath") {
    attributes {
        attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "transformed-jar")
    }
}
// end::artifact-transform-use[]

// tag::artifact-transform-registration[]
dependencies {
    registerTransform(MyTransform::class) {
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "transformed-jar")
    }
}
// end::artifact-transform-registration[]

tasks.register("resolve") {
    val files = configurations.runtimeClasspath.get().incoming.files
    val fileNames = files.map { it.name }
    doLast {
        check(fileNames.contains("guava-33.2.1-jre-transformed.jar"))
    }
}
