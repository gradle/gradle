import me.lucko.jarrelocator.JarRelocator
import me.lucko.jarrelocator.Relocation
import java.util.jar.JarFile
import java.util.stream.Collectors

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("me.lucko:jar-relocator:1.3")
    }
}

// tag::artifact-transform-relocate[]
@CacheableTransform                                                          // <1>
abstract class ClassRelocator : TransformAction<ClassRelocator.Parameters> {
    interface Parameters : TransformParameters {                             // <2>
        @get:CompileClasspath                                                // <3>
        val externalClasspath: ConfigurableFileCollection
        @get:Input
        val excludedPackage: Property<String>
    }

    @get:Classpath                                                           // <4>
    @get:InputArtifact
    abstract val primaryInput: Provider<FileSystemLocation>

    @get:CompileClasspath
    @get:InputArtifactDependencies                                           // <5>
    abstract val dependencies: FileCollection

    override
    fun transform(outputs: TransformOutputs) {
        val primaryInputFile = primaryInput.get().asFile
        if (parameters.externalClasspath.contains(primaryInputFile)) {       // <6>
            outputs.file(primaryInput)
        } else {
            val baseName = primaryInputFile.name.substring(0, primaryInputFile.name.length - 4)
            relocateJar(outputs.file("$baseName-relocated.jar"))
        }
    }

    private fun relocateJar(output: File) {
        // implementation...
        val relocatedPackages = (dependencies.flatMap { it.readPackages() } + primaryInput.get().asFile.readPackages()).toSet()
        val nonRelocatedPackages = parameters.externalClasspath.flatMap { it.readPackages() }
        val relocations = (relocatedPackages - nonRelocatedPackages).map { packageName ->
            val toPackage = "relocated.$packageName"
            println("$packageName -> $toPackage")
            Relocation(packageName, toPackage)
        }
        JarRelocator(primaryInput.get().asFile, output, relocations).run()
    }
// end::artifact-transform-relocate[]

    private fun File.readPackages(): Set<String> {
        return JarFile(this).use { jarFile ->
            return jarFile.stream()
                .filter { !it.isDirectory }
                .filter { it.name.endsWith(".class") }
                .map { entry ->
                    entry.name.substringBeforeLast('/').replace('/', '.')
                }
                .collect(Collectors.toSet())
        }
    }
// tag::artifact-transform-relocate[]
}
// end::artifact-transform-relocate[]

configurations.create("externalClasspath")

val usage = Attribute.of("usage", String::class.java)
val artifactType = Attribute.of("artifactType", String::class.java)

dependencies {
    registerTransform(ClassRelocator::class) {
        from.attribute(artifactType, "jar")
        to.attribute(artifactType, "relocated-classes")
        parameters {
            externalClasspath.from(configurations.getByName("externalClasspath"))
            excludedPackage = "org.gradle.api"
        }
    }
}

dependencies {
    attributesSchema {
        attribute(usage)
    }
}
configurations.create("compile") {
    attributes.attribute(usage, "api")
}
