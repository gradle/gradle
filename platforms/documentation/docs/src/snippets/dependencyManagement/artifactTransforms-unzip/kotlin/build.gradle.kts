import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

import org.gradle.api.artifacts.transform.TransformParameters

// tag::artifact-transform-unzip[]
abstract class Unzip : TransformAction<TransformParameters.None> {          // <1>
    @get:InputArtifact                                                      // <2>
    abstract val inputArtifact: Provider<FileSystemLocation>

    override
    fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        val unzipDir = outputs.dir(input.name)                              // <3>
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

val usage = Attribute.of("usage", String::class.java)
// tag::artifact-transform-registration[]
val artifactType = Attribute.of("artifactType", String::class.java)

dependencies {
    registerTransform(Unzip::class) {
        from.attribute(artifactType, "jar")
        to.attribute(artifactType, "java-classes-directory")
    }
}
// end::artifact-transform-registration[]

dependencies {
    attributesSchema {
        attribute(usage)
    }
}
configurations.create("compile") {
    attributes.attribute(usage, "api")
}
