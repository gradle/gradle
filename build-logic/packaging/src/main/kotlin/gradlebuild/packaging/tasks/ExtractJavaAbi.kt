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

package gradlebuild.packaging.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.tools.api.ApiClassExtractor
import org.gradle.internal.tools.api.impl.JavaApiMemberWriter
import java.io.File
import java.io.InputStream


/**
 * Extract API-only classes from classes directories.
 *
 * Keeps only the following:
 *
 * -  API stubs of public classes in the specified packages
 * - `META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule`
 * - `META-INF/services/org.codehaus.groovy.transform.ASTTransformation`
 * - `META-INF/\*.kotlin_module`
 */
@CacheableTask
abstract class ExtractJavaAbi : DefaultTask() {

    @get:Input
    abstract val packages: SetProperty<String>

    @get:CompileClasspath
    abstract val classesDirectories: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun execute() {
        val apiClassExtractor = with(ApiClassExtractor.withWriter(JavaApiMemberWriter.adapter())) {
            val publicApiPackages = packages.get()
            if (publicApiPackages.isNotEmpty()) {
                includePackagesMatching(publicApiPackages::contains)
            } else {
                includePackagePrivateMembers()
            }
            build()
        }

        // Walk the classesDirectory and find each `.class` file
        classesDirectories.forEach { classDir ->
            classDir.walk().forEach { inputClassFile ->
                val relativePath = inputClassFile.relativeTo(classDir).path
                val outputClassFile = outputDirectory.get().asFile.resolve(relativePath)
                when (inputClassFile.filtering()) {
                    ContentFilter.VERBATIM -> {
                        outputClassFile.parentFile.mkdirs()
                        inputClassFile.copyTo(outputClassFile)
                    }

                    ContentFilter.API_ONLY -> {
                        inputClassFile.inputStream().use { input ->
                            apiClassExtractor.extractApiClassFrom(input)
                                .ifPresent { apiClass ->
                                    outputClassFile.parentFile.mkdirs()
                                    outputClassFile.outputStream().use { output -> output.write(apiClass) }
                                }
                        }
                    }

                    ContentFilter.SKIP -> {
                        // Skip the file
                    }
                }
            }
        }
    }

    private
    fun ApiClassExtractor.extractApiClassFrom(input: InputStream) =
        extractApiClassFrom(input.readAllBytes())

    private
    fun File.filtering(): ContentFilter {
        if (name.endsWith(".class")) {
            return if (name.endsWith("/module-info.class")
                || name.endsWith("/package-info.class")
            ) {
                ContentFilter.VERBATIM
            } else {
                ContentFilter.API_ONLY
            }
        }
        if (name.equals("META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule")) {
            return ContentFilter.VERBATIM
        }
        if (name.equals("META-INF/services/org.codehaus.groovy.transform.ASTTransformation")) {
            return ContentFilter.VERBATIM
        }
        if (name.matches(KOTLIN_MODULE_PATH)) {
            return ContentFilter.VERBATIM
        }
        return ContentFilter.SKIP
    }

    private
    enum class ContentFilter {
        VERBATIM,
        API_ONLY,
        SKIP
    }

    companion object {
        val KOTLIN_MODULE_PATH = Regex("META-INF/.*\\.kotlin_module")
    }
}
