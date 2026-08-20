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

import com.google.common.io.Files
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject


@CacheableTransform
abstract class Minify : TransformAction<Minify.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        var minifySpecsByCoordinates: Map<String, MinifySpec>

        @get:Classpath
        val minifierClasspath: ConfigurableFileCollection

        @get:Input
        val minifierJavaVersion: Property<Int>

        @get:Internal
        val minifierJdkHome: DirectoryProperty
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val artifact: Provider<FileSystemLocation>

    @get:Inject
    abstract val execOperations: ExecOperations

    private
    val jarArtifactRegex = Regex("""^(.*?)-\d+(\.\d+)*([.-][A-Za-z0-9]+)*\.jar$""")

    private val specsByArtifacts: Map<String, MinifySpec> by lazy {
        parameters.minifySpecsByCoordinates.mapKeys { it.key.substringAfter(":") }
    }

    override fun transform(outputs: TransformOutputs) {
        val fileName = artifact.get().asFile.name
        val artifactName = extractArtifactName(fileName)
        val spec = specsByArtifacts[artifactName]
        if (spec == null) {
            outputs.file(artifact)
        } else {
            val nameWithoutExtension = Files.getNameWithoutExtension(fileName)
            minify(artifact.get().asFile, spec, outputs.file("$nameWithoutExtension-min.jar"))
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
        if (!spec.removesUnreachable) {
            MinifyPreprocessor.preprocess(artifact, output, spec, parameters.minifierJavaVersion.get())
            return
        }
        val shrunk = if (spec.needsPreprocessing) {
            File.createTempFile("preprocessed", ".jar").apply {
                MinifyPreprocessor.preprocess(artifact, this, spec, parameters.minifierJavaVersion.get())
            }
        } else {
            artifact
        }
        val rules = File.createTempFile("minify", ".pro")
        rules.writeText(minifyRules(spec).joinToString("\n"))
        val log = ByteArrayOutputStream()
        try {
            val result = execOperations.javaexec {
                classpath = parameters.minifierClasspath
                mainClass.set("com.android.tools.r8.R8")
                args(
                    "--classfile",
                    "--no-desugaring",
                    "--lib", parameters.minifierJdkHome.get().asFile.absolutePath,
                    "--pg-conf", rules.absolutePath,
                    "--output", output.absolutePath,
                    shrunk.absolutePath
                )
                standardOutput = log
                errorOutput = log
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                throw IllegalStateException("Could not minify $artifact:\n$log")
            }
        } finally {
            rules.delete()
            if (shrunk !== artifact) {
                shrunk.delete()
            }
        }
    }

    private
    fun minifyRules(spec: MinifySpec) = listOf(
        "-dontobfuscate",
        "-dontoptimize",
        "-keepattributes *",
        "-keep,allowshrinking class ** { *; }",
        "-dontwarn"
    ) + spec.keepClasses.map { keepClass ->
        // R8 takes the first pattern of the list that matches, so the exclusions have to come first.
        val exclusions = spec.excludedClasses.filterNot { keepClass.startsWith(it.substringBefore("*")) }
        "-keep,includedescriptorclasses class ${(exclusions.map { "!$it" } + keepClass).joinToString(",")} { *; }"
    }
}
