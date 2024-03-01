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

import gradlebuild.integrationtests.action.AnnotationGeneratorWorkAction
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
// Using star import to workaround https://youtrack.jetbrains.com/issue/KTIJ-24390
import org.gradle.kotlin.dsl.*
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject


@CacheableTask
abstract class GenerateLanguageAnnotations : DefaultTask() {
    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @get:CompileClasspath
    abstract val classpath: ConfigurableFileCollection

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val destDir: DirectoryProperty

    @TaskAction
    fun generateAnnotations() {
        val queue = workerExecutor.classLoaderIsolation {
            classpath = this@GenerateLanguageAnnotations.classpath
        }
        queue.submit(AnnotationGeneratorWorkAction::class) {
            packageName = this@GenerateLanguageAnnotations.packageName
            destDir = this@GenerateLanguageAnnotations.destDir
        }
    }
}
