import org.gradle.api.attributes.LibraryElements
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

plugins {
    application
}

val artifactType = Attribute.of("artifactType", String::class.java)
val buildType = Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java)
val flavor = Attribute.of("com.android.build.api.attributes.ProductFlavor:org.gradle.example.my-own-flavor", String::class.java)

configurations.all {
    if (isCanBeResolved && !isCanBeConsumed) {
        attributes {
            attribute(artifactType, "jar")
            attribute(buildType, "release")
            // The android libs have flavors, and we need to choose one
            attribute(flavor, "full")
        }
    }
}

dependencies {
    implementation("example:java-library:1.0")
    implementation("example:kotlin-library:1.0")
    // can extract JAR from Android libs thanks to compatibility and transform defined below
    implementation("example:android-library:1.0")
    implementation("example:android-library-single-variant:1.0")
    implementation("example:android-kotlin-library:1.0")
    implementation("example:kotlin-multiplatform-android-library:1.0")
    // selects JVM variant because of 'usage' attribute
    implementation("example:kotlin-multiplatform-library:1.0")
}

application {
    mainClass = "myapp.App"
}


// AAR processing:
dependencies {
    registerTransform(JarExtraction::class.java) {
        from.attribute(artifactType, "aar")
        to.attribute(artifactType, LibraryElements.JAR)
    }
    attributesSchema {
        getMatchingStrategy(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE).compatibilityRules.add(
                AarJarCompatibility::class.java)
    }
}
class AarJarCompatibility : AttributeCompatibilityRule<LibraryElements> {
    override fun execute(details: CompatibilityCheckDetails<LibraryElements>) {
        // We accept aars on the classpath as we can transform them into jars (see JarExtraction below)
        if (details.producerValue?.name == "aar") {
            details.compatible()
        }
    }
}
abstract class JarExtraction : TransformAction<TransformParameters.None?> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>
    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        val output = outputs.file(input.nameWithoutExtension + ".jar")
        extractJar(input, output)
    }
    private fun extractJar(input: File, output: File) {
        ZipFile(input).use { aarFile ->
            val zipEntry = aarFile.getEntry("classes.jar")
            aarFile.getInputStream(zipEntry).use { jarFile ->
                Files.copy(jarFile, output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
