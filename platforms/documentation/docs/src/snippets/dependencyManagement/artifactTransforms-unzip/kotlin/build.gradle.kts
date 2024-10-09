import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

import org.gradle.api.artifacts.transform.TransformParameters

plugins {
    id("java-library")
}

// tag::artifact-transform-unzip[]
abstract class Unzip : TransformAction<TransformParameters.None> {          // <1>
    @get:InputArtifact                                                      // <2>
    abstract val inputArtifact: Provider<FileSystemLocation>

    override
    fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        val unzipDir = outputs.dir(input.name + "-unzipped")                // <3>
        unzipTo(input, unzipDir)                                            // <4>
    }

    private fun unzipTo(zipFile: File, unzipDir: File) {
        // implementation...
// end::artifact-transform-unzip[]
        ZipFile(zipFile).use { zip ->
            val outputDirectoryCanonicalPath = unzipDir.canonicalPath
            for (entry in zip.entries()) {
                unzipEntryTo(unzipDir, outputDirectoryCanonicalPath, zip, entry)
            }
        }
    }

    private fun unzipEntryTo(outputDirectory: File, outputDirectoryCanonicalPath: String, zip: ZipFile, entry: ZipEntry) {
        val output = outputDirectory.resolve(entry.name)
        if (!output.canonicalPath.startsWith(outputDirectoryCanonicalPath)) {
            throw ZipException("Zip entry '${entry.name}' is outside of the output directory")
        }
        if (entry.isDirectory) {
            output.mkdirs()
        } else {
            output.parentFile.mkdirs()
            zip.getInputStream(entry).use { Files.copy(it, output.toPath()) }
        }
// tag::artifact-transform-unzip[]
    }
}
// end::artifact-transform-unzip[]

// tag::artifact-transform-registration[]
dependencies {
    registerTransform(Unzip::class.java) {
        from.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named<LibraryElements>(LibraryElements.JAR))
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
        to.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named<LibraryElements>(LibraryElements.CLASSES_AND_RESOURCES))
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
    }
}
// end::artifact-transform-registration[]

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

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.junit.jupiter:junit-jupiter-api:5.11.0")
}

// tag::resolve-transformed-files[]
tasks.register<ResolveFiles>("resolveTransformedFiles") {
    files.from(configurations.runtimeClasspath.map {
        it.incoming.artifactView {
            attributes {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES_AND_RESOURCES))
                attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
            }
        }.files
    })
}
// end::resolve-transformed-files[]
