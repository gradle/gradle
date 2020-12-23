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

package gradlebuild.integrationtests.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject


/**
 * Executes the annotation generation logic from the `:test-annotation-generator` project.
 */
@CacheableTask
abstract class GenerateLanguageAnnotations : DefaultTask() {
    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val destDir: DirectoryProperty

    @TaskAction
    fun generateAnnotations() {
        val queue = workerExecutor.noIsolation()
        queue.submit(AnnotationGeneratorWorkAction::class.java) {
            generatorClasspath.setFrom(classpath)
            packageName.set(this@GenerateLanguageAnnotations.packageName)
            destDir.set(this@GenerateLanguageAnnotations.destDir)
        }
    }
}


internal
interface AnnotationGeneratorParameters : WorkParameters {
    val generatorClasspath: ConfigurableFileCollection
    val packageName: Property<String>
    val destDir: DirectoryProperty
}


internal
abstract class AnnotationGeneratorWorkAction : WorkAction<AnnotationGeneratorParameters> {
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun execute() {
        execOperations.javaexec {
            classpath = parameters.generatorClasspath
            main = "org.gradle.internal.test.annotation.generator.AnnotationGeneratorKt"
            isIgnoreExitValue = false
            args(parameters.packageName.get(), parameters.destDir.get().asFile.absolutePath)
        }
    }
}
