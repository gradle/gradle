/*
 * Copyright 2024 the original author or authors.
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

package gradlebuild.packaging.transforms

import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.internal.tools.api.ApiClassExtractor
import org.gradle.internal.tools.api.impl.JavaApiMemberWriter
import java.io.File
import java.io.InputStream
import java.util.Optional
import java.util.zip.ZipEntry
import java.util.zip.ZipFile


/**
 * Extract the public API classes from a jar file.
 *
 * Keeps only the following:
 *
 * -  API stubs of public classes in the specified packages
 * - `META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule`
 * - `META-INF/services/org.codehaus.groovy.transform.ASTTransformation`
 * - `META-INF/\*.kotlin_module`
 */
@CacheableTransform
abstract class ShrinkPublicApiClassesTransform : TransformAction<ShrinkPublicApiClassesTransform.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        val packages: SetProperty<String>
    }

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val apiClassExtractor = with(ApiClassExtractor.withWriter(JavaApiMemberWriter.adapter())) {
            val publicApiPackages = parameters.packages.get()
            if (publicApiPackages.isNotEmpty()) {
                includePackagesMatching(publicApiPackages::contains)
            }
            build()
        }
        val jarFile = inputArtifact.get().asFile
        val zipFile = ZipFile(jarFile)
        val outputRoot = outputs.dir("public-api")
        zipFile.stream().forEach { entry ->
            entry.filtering().ifPresent { filtering ->
                zipFile.getInputStream(entry).use { input ->
                    when (filtering) {
                        ContentFilter.VERBATIM ->
                            outputRoot.withOutputStream(entry.name) { output -> input.copyTo(output) }

                        ContentFilter.API_ONLY ->
                            apiClassExtractor.extractApiClassFrom(input)
                                .ifPresent { apiClass ->
                                    outputRoot.withOutputStream(entry.name) { output -> output.write(apiClass) }
                                }
                    }
                }
            }
        }
    }

    private
    fun ApiClassExtractor.extractApiClassFrom(input: InputStream) =
        extractApiClassFrom(input.readAllBytes())

    private
    fun File.withOutputStream(path: String, action: (output: java.io.OutputStream) -> Unit) {
        val outputFile = resolve(path)
        outputFile.parentFile.mkdirs()
        outputFile.outputStream().use(action)
    }

    private
    fun ZipEntry.filtering(): Optional<ContentFilter> {
        if (name.endsWith(".class")) {
            return if (name.endsWith("/module-info.class")
                || name.endsWith("/package-info.class")
            ) {
                Optional.of(ContentFilter.VERBATIM)
            } else {
                Optional.of(ContentFilter.API_ONLY)
            }
        }
        if (name.equals("META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule")) {
            return Optional.of(ContentFilter.VERBATIM)
        }
        if (name.equals("META-INF/services/org.codehaus.groovy.transform.ASTTransformation")) {
            return Optional.of(ContentFilter.VERBATIM)
        }
        if (name.matches(KOTLIN_MODULE_PATH)) {
            return Optional.of(ContentFilter.VERBATIM)
        }
        return Optional.empty()
    }

    private
    enum class ContentFilter {
        VERBATIM,
        API_ONLY
    }

    companion object {
        val KOTLIN_MODULE_PATH = Regex("META-INF/.*\\.kotlin_module")
    }
}
