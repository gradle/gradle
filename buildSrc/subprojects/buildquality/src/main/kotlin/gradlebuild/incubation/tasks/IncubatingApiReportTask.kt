package gradlebuild.incubation.tasks

import gradlebuild.incubation.action.IncubatingApiReportWorkAction
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject


@CacheableTask
abstract class IncubatingApiReportTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val versionFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releasedVersionsFile: RegularFileProperty

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

    @TaskAction
    fun analyze() = workerExecutor.noIsolation().submit(IncubatingApiReportWorkAction::class) {
        srcDirs.from(this@IncubatingApiReportTask.sources)
        htmlReportFile.set(this@IncubatingApiReportTask.htmlReportFile)
        textReportFile.set(this@IncubatingApiReportTask.textReportFile)
        title.set(this@IncubatingApiReportTask.title)
        releasedVersionsFile.set(this@IncubatingApiReportTask.releasedVersionsFile)
    }
}
