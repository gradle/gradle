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

import gradlebuild.removal.action.RemovalReportAggregationWorkAction
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
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
abstract class NextMajorRemovalAggregateReportTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reports: ConfigurableFileCollection

    @get:Input
    abstract val currentCommit: Property<String>

    /** The next major Gradle version the report targets (e.g. 10 while developing 9.x). */
    @get:Input
    abstract val targetMajorVersion: Property<Int>

    @get:OutputFile
    abstract val htmlReportFile: RegularFileProperty

    @get:OutputFile
    abstract val csvReportFile: RegularFileProperty

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun generateReport() = workerExecutor.noIsolation().submit(RemovalReportAggregationWorkAction::class) {
        reports.from(this@NextMajorRemovalAggregateReportTask.reports)
        htmlReportFile = this@NextMajorRemovalAggregateReportTask.htmlReportFile
        csvReportFile = this@NextMajorRemovalAggregateReportTask.csvReportFile
        currentCommit = this@NextMajorRemovalAggregateReportTask.currentCommit
        targetMajorVersion = this@NextMajorRemovalAggregateReportTask.targetMajorVersion
    }
}
