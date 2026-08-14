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
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject


/**
 * Extract API-only classes from classes directories.
 *
 * Keeps only the following:
 *
 * - API stubs of the classes, including the package-private ones
 * - `META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule`
 * - `META-INF/services/org.codehaus.groovy.transform.ASTTransformation`
 * - `META-INF/\*.kotlin_module`
 */
@CacheableTask
abstract class ExtractJavaAbi : DefaultTask() {

    // Do not change this to CompileClasspath. It hashes the input with the ABI extractor of the
    // distribution that runs this build, and that extractor drops the details this task keeps.
    // A new type-use annotation then leaves this task up to date, and the stub jar keeps the
    // old annotations.
    @get:Classpath
    abstract val classesDirectories: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Classpath
    abstract val extractorClasspath: ConfigurableFileCollection

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun execute() {
        val task = this
        // The worker process keeps extraction failures and heap out of the build process,
        // IsolatedApiClassExtractor keeps the extractor away from the distribution running this build
        workerExecutor.processIsolation().submit(ExtractJavaAbiAction::class.java) {
            classesDirectories.setFrom(task.classesDirectories)
            outputDirectory.set(task.outputDirectory)
            extractorClasspath.setFrom(task.extractorClasspath)
        }
    }

    abstract class ExtractJavaAbiAction @Inject constructor() : WorkAction<ExtractJavaAbiAction.Params> {

        interface Params : WorkParameters {
            val classesDirectories: ConfigurableFileCollection
            val outputDirectory: DirectoryProperty
            val extractorClasspath: ConfigurableFileCollection
        }

        override fun execute() {
            val outputDirectory = parameters.outputDirectory.get().asFile
            IsolatedApiClassExtractor.runUsing(parameters.extractorClasspath) { extractor ->
                parameters.classesDirectories.forEach { classDir ->
                    classDir.walk().forEach { inputFile ->
                        val relativePath = inputFile.relativeTo(classDir).invariantSeparatorsPath
                        val outputFile = outputDirectory.resolve(relativePath)
                        when (contentFilterFor(relativePath)) {
                            ContentFilter.VERBATIM -> {
                                outputFile.parentFile.mkdirs()
                                inputFile.copyTo(outputFile, overwrite = true)
                            }

                            ContentFilter.API_ONLY -> {
                                extractor.extractApiClassFrom(inputFile.readBytes())
                                    .ifPresent { apiClass ->
                                        outputFile.parentFile.mkdirs()
                                        outputFile.writeBytes(apiClass)
                                    }
                            }

                            ContentFilter.SKIP -> {
                                // Skip the file
                            }
                        }
                    }
                }
            }
        }
    }
}
