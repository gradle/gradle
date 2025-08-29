package gradlebuild.incubation.tasks

import gradlebuild.incubation.action.IncubatingApiReportWorkAction
import gradlebuild.modules.extension.ExternalModulesExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
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
// Using star import to workaround https://youtrack.jetbrains.com/issue/KTIJ-24390
import org.gradle.kotlin.dsl.*
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject


@CacheableTask
abstract class IncubatingApiReportTask : DefaultTask() {

    private val additionalClasspath = project.objects.fileCollection().apply {
        from(
            project.configurations.detachedConfiguration(
                project.dependencies.create(project.the<ExternalModulesExtension>().futureKotlin("compiler-embeddable"))
            )
        )
    }

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

    @get:Inject
    abstract val layout: ProjectLayout

    @TaskAction
    fun analyze() = workerExecutor.processIsolation { classpath.from(additionalClasspath) }.submit(IncubatingApiReportWorkAction::class) {
        repositoryRoot = layout.settingsDirectory
        srcDirs.from(this@IncubatingApiReportTask.sources)
        htmlReportFile = this@IncubatingApiReportTask.htmlReportFile
        textReportFile = this@IncubatingApiReportTask.textReportFile
        title = this@IncubatingApiReportTask.title
        releasedVersionsFile = this@IncubatingApiReportTask.releasedVersionsFile
    }
}
