import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

// tag::url-process[]
public abstract class UrlProcess extends DefaultTask {
    // Use an abstract getter method annotated with @Nested
    @Nested
    abstract HostAndPath getHostAndPath();

    @TaskAction
    void run() {
        // Use the `hostAndPath` property
        System.out.println("Downloading https://" + getHostAndPath().getHostName().get() + "/" + getHostAndPath().getPath().get());
    }
}
// end::url-process[]
