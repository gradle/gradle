import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class MyCreateFileTask extends DefaultTask {
    @Input
    abstract Property<String> getFileText()

    @Input
    String filePath = project.layout.settingsDirectory.file("myfile.txt").asFile.path

    @OutputFile
    File myFile = new File(filePath)

    @TaskAction
    void action() {
        myFile.createNewFile()
        myFile.text = fileText.get()
    }
}

class MyCreateFileBinaryPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.tasks.register("createFileTaskFromBinaryPlugin", MyCreateFileTask) {
            group = "from my binary plugin"
            description = "Create myfile.txt in the current directory"
            fileText.set("HELLO FROM MY BINARY PLUGIN")
        }
    }
}
