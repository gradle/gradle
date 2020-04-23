import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.net.URI;

public abstract class Download extends DefaultTask {
    // Use an abstract getter method
    @Input
    abstract Property<URI> getUri();

    @TaskAction
    void run() {
        // Use the `uri` property
        System.out.println("Downloading " + getUri().get());
    }
}
