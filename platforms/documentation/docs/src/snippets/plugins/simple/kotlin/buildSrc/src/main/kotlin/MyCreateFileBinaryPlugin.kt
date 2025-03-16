import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class CreateFileTask : DefaultTask() {
    @get:Input
    abstract val fileText: Property<String>

    @Input
    val filePath = project.layout.settingsDirectory.file("myfile.txt").asFile.path

    @OutputFile
    val myFile: File = File(filePath)

    @TaskAction
    fun action() {
        myFile.createNewFile()
        myFile.writeText(fileText.get())
    }
}

class MyCreateFileBinaryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("createFileTaskFromBinaryPlugin", CreateFileTask::class.java) {
            group = "from my binary plugin"
            description = "Create myfile.txt in the current directory"
            fileText.set("HELLO FROM MY BINARY PLUGIN")
        }
    }
}
