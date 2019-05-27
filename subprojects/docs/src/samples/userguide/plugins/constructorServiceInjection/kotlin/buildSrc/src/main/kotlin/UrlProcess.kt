import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory

open class UrlProcess
// Inject an ObjectFactory into the constructor
@Inject constructor(objectFactory: ObjectFactory) : DefaultTask() {
    // Use the factory
    @OutputDirectory
    val outputDirectory = objectFactory.directoryProperty()

    @TaskAction
    fun run() {
        // ...
    }
}
