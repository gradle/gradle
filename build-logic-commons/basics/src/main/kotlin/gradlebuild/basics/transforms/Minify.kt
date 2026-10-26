/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gradlebuild.basics.transforms

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject


@CacheableTransform
abstract class Minify : TransformAction<Minify.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        var minifySpecsByCoordinates: Map<String, MinifySpec>

        @get:Classpath
        val minifierClasspath: ConfigurableFileCollection

        @get:Internal
        val platformLibrary: RegularFileProperty

        @get:Classpath
        val minifiedLibraries: ConfigurableFileCollection

        @get:Classpath
        val minifierJmods: ConfigurableFileCollection
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val artifact: Provider<FileSystemLocation>

    @get:Inject
    abstract val execOperations: ExecOperations

    private
    val jarArtifactRegex = Regex("""^(.*?)-\d+(\.\d+)*([.-][A-Za-z0-9]+)*\.jar$""")

    private
    val specsByArtifacts: Map<String, MinifySpec> by lazy {
        parameters.minifySpecsByCoordinates.mapKeys { it.key.substringAfter(":") }
    }

    override fun transform(outputs: TransformOutputs) {
        val fileName = artifact.get().asFile.name
        val artifactName = extractArtifactName(fileName)
        val spec = specsByArtifacts[artifactName]
        if (spec == null) {
            outputs.file(artifact)
        } else {
            minify(artifact.get().asFile, spec, outputs.file("${artifact.get().asFile.nameWithoutExtension}-min.jar"))
        }
    }

    private
    fun extractArtifactName(fileName: String): String {
        return jarArtifactRegex.matchEntire(fileName)
            ?.groupValues
            ?.get(1)
            ?: error("Cannot derive artifact name from: $fileName")
    }

    private
    fun minify(artifact: File, spec: MinifySpec, output: File) {
        val input = if (spec.removeClassesShippedBy.isEmpty()) artifact else withoutClassesShippedBy(artifact, spec.removeClassesShippedBy)
        val rules = File.createTempFile("minify", ".pro")
        rules.writeText(minifyRules(artifact, input, spec, output).joinToString("\n"))
        val log = ByteArrayOutputStream()
        try {
            val result = execOperations.javaexec {
                classpath = parameters.minifierClasspath
                mainClass.set("proguard.ProGuard")
                maxHeapSize = "3g"
                args("@${rules.absolutePath}")
                standardOutput = log
                errorOutput = log
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                throw IllegalStateException("Could not minify $artifact:\n$log")
            }
        } finally {
            rules.delete()
            if (input != artifact) {
                input.delete()
            }
        }
    }

    // There are too many classes for an input filter of the minifier
    private
    fun withoutClassesShippedBy(artifact: File, libraries: Set<String>): File {
        val shipped = libraries.flatMapTo(HashSet(), ::classesShippedBy)
        val filtered = File.createTempFile("minify", ".jar")
        ZipFile(artifact).use { zip ->
            ZipOutputStream(filtered.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    if (entry.name !in shipped) {
                        out.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                        zip.getInputStream(entry).use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
            }
        }
        return filtered
    }

    private
    fun generatedJdkJmods(): File {
        val library = parameters.platformLibrary.asFile.get()
        if (library.isFile) {
            return library
        }
        val modules = FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules")
        val written = mutableSetOf<String>()
        val partial = File.createTempFile("platform", ".jar", library.parentFile.apply { mkdirs() })
        ZipOutputStream(partial.outputStream().buffered()).use { out ->
            Files.walk(modules).use { paths ->
                paths.filter { it.toString().endsWith(".class") }.forEach { path ->
                    val name = modules.relativize(path).toString().substringAfter('/')
                    if (name != "module-info.class" && written.add(name)) {
                        out.putNextEntry(ZipEntry(name))
                        Files.copy(path, out)
                        out.closeEntry()
                    }
                }
            }
        }
        if (!partial.renameTo(library)) {
            error("Minification: could not move the class library of the current JVM to $library")
        }
        return library
    }

    private
    fun classesShippedBy(coordinates: String): List<String> {
        val name = coordinates.substringAfter(":")
        val library = parameters.minifiedLibraries.files.singleOrNull { jarArtifactRegex.matchEntire(it.name)?.groupValues?.get(1) == name }
            ?: error("Minification: $coordinates is not a minifier library")
        // The classes that the minification of that library removes are not shipped either
        val removed = specsByArtifacts[name]?.removePackages.orEmpty().map { it.replace('.', '/') + "/" }
        return ZipFile(library).use { zip ->
            zip.entries().asSequence().map { it.name }.filter { it.endsWith(".class") && removed.none(it::startsWith) }.toList()
        }
    }

    private
    fun minifyRules(artifact: File, input: File, spec: MinifySpec, output: File) = buildList {
        val removedPackages = spec.removePackages.map { "**${it.replace('.', '/')}/**" }

        val inputFilters = listOf("!META-INF/*.SF", "!META-INF/*.RSA", "!META-INF/*.DSA", "!META-INF/*.EC") +
            removedPackages.map { "!$it" }
        add("-injars '${input.absolutePath}'(${inputFilters.joinToString(",")})")
        add("-outjars '${output.absolutePath}'")
        add("-libraryjars '${generatedJdkJmods().absolutePath}'")
        if (removedPackages.isNotEmpty()) {
            add("-libraryjars '${artifact.absolutePath}'(${removedPackages.joinToString(",")})")
        }
        for (library in parameters.minifiedLibraries.files - artifact) {
            add("-libraryjars '${library.absolutePath}'")
        }

        add("-keepnames class ** { *; }")
        if (spec.dropLocalVariables) {
            add("-keepattributes !LocalVariableTable,!LocalVariableTypeTable,*")
        } else {
            add("-keepattributes *")
        }
        add("-dontwarn")
        add("-dontnote")
        if (!spec.removeUnreachable) {
            add("-dontshrink")
        }
        if (spec.sideEffectFreeCalls.isEmpty()) {
            add("-dontoptimize")
        }

        for (keepClass in spec.keepClasses) {
            val exclusions = spec.excludedClasses.filterNot { keepClass.startsWith(it.substringBefore("*")) }
            val patterns = exclusions.map { "!$it" } + keepClass
            add("-keep,includedescriptorclasses class ${patterns.joinToString(",")} { *; }")
        }

        for (call in spec.sideEffectFreeCalls) {
            add("-assumenosideeffects class ${call.substringBefore('#')} { ${call.substringAfter('#')}; }")
        }
    }
}
