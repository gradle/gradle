import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.net.URI;

public abstract class UrlProcess extends DefaultTask {
    // Use an abstract getter and setter method
    abstract URI getUri();
    abstract void setUri(URI uri);

    @TaskAction
    void run() {
        // Use the `uri` property
        System.out.println("Downloading " + getUri());
    }
}
