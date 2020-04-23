import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

// tag::download[]
public abstract class Download extends DefaultTask {
    // Use an abstract getter method annotated with @Nested
    @Nested
    abstract Resource getResource();

    @TaskAction
    void run() {
        // Use the `resource` property
        System.out.println("Downloading https://" + getResource().getHostName().get() + "/" + getResource().getPath().get());
    }
}
// end::download[]
