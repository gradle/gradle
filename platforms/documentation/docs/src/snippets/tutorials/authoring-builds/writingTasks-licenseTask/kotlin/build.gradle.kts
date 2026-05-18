plugins {
    id("java")
}

// tag::license-task[]
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

class LicensePlugin: Plugin<Project> {
    // Don't change anything here
    override fun apply(project: Project) { }
}

abstract class LicenseTask : DefaultTask() {
    @Input
    val licenseFilePath = project.layout.settingsDirectory.file("license.txt").asFile.path

    @TaskAction
    fun action() {
        // Read the license text
        val licenseText = File(licenseFilePath).readText()
        // Walk the directories looking for java files
        project.layout.settingsDirectory.asFile.walk().forEach {
            if (it.extension == "java") {
                // Read the source code
                var ins: InputStream = it.inputStream()
                var content = ins.readBytes().toString(Charset.defaultCharset())
                // Write the license and the source code to the file
                it.writeText(licenseText + content)
            }
        }
    }
}
// end::license-task[]
