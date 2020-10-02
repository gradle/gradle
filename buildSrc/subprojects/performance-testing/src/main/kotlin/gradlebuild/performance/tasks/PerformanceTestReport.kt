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

package gradlebuild.performance.tasks

import gradlebuild.performance.reporter.PerformanceReporter
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileTree
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations
import javax.inject.Inject


abstract class PerformanceTestReport : DefaultTask() {

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:Input
    abstract val reportGeneratorClass: Property<String>

    @get:Internal
    abstract val performanceResultsDirectory: DirectoryProperty

    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFiles
    val performanceResults: FileTree
        get() = performanceResultsDirectory.asFileTree.matching { include("**/*.json") }

    @get:OutputDirectory
    abstract val reportDir: DirectoryProperty

    @get:Optional
    @get:Input
    val databaseUrl: Provider<String>
        get() = databaseParameters.getting("org.gradle.performance.db.url")

    @get:Internal
    abstract val databaseParameters: MapProperty<String, String>

    @get:Input
    abstract val branchName: Property<String>

    @get:Option(option = "channel", description = "Channel to use when running the performance test. By default, 'commits'.")
    @get:Input
    abstract val channel: Property<String>

    @get:Input
    abstract val commitId: Property<String>

    @get:Input
    abstract val projectName: Property<String>

    @get:Inject
    abstract val fileOperations: FileSystemOperations

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generateReport() {
        val reporter = PerformanceReporter(execOperations, fileOperations)
        reporter.report(
            reportGeneratorClass.get(),
            reportDir.get().asFile,
            performanceResults,
            databaseParameters.get(),
            channel.get(),
            branchName.get(),
            commitId.get(),
            classpath,
            projectName.get()
        )
    }
}
