/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.removal.tasks

import gradlebuild.removal.action.RemovalReportWorkAction
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
// Using star import to workaround https://youtrack.jetbrains.com/issue/KTIJ-24390
import org.gradle.kotlin.dsl.*
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject


@CacheableTask
abstract class Gradle10RemovalReportTask : DefaultTask() {

    // Mirrors IncubatingApiReportTask: the worker hosts the Kotlin compiler embeddable to parse Kotlin sources.
    private val additionalClasspath = project.objects.fileCollection().apply {
        val libs = project.the<VersionCatalogsExtension>().named("buildLibs")
        from(
            project.configurations.detachedConfiguration(
                project.dependencies.create(libs.findLibrary("kotlinCompilerEmbeddable").get().get().copy().apply {
                    version {
                        strictly(embeddedKotlinVersion)
                    }
                }),
            )
        )
    }

    @get:Input
    abstract val title: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val htmlReportFile: RegularFileProperty

    @get:OutputFile
    abstract val textReportFile: RegularFileProperty

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @get:Inject
    abstract val layout: ProjectLayout

    @TaskAction
    fun analyze() = workerExecutor.processIsolation { classpath.from(additionalClasspath) }.submit(RemovalReportWorkAction::class) {
        repositoryRoot = layout.settingsDirectory
        srcDirs.from(this@Gradle10RemovalReportTask.sources)
        htmlReportFile = this@Gradle10RemovalReportTask.htmlReportFile
        textReportFile = this@Gradle10RemovalReportTask.textReportFile
        title = this@Gradle10RemovalReportTask.title
    }
}
