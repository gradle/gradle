import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

class Download extends DefaultTask {
    @OutputDirectory
    final DirectoryProperty outputDirectory

    // Inject an ObjectFactory into the constructor
    @Inject
    Download(ObjectFactory objectFactory) {
        // Use the factory
        outputDirectory = objectFactory.directoryProperty()
    }

    @TaskAction
    void run() {
        // ...
    }
}
