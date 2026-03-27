import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.net.URI;

public abstract class Download extends DefaultTask {
    // This property provides access to the service instance
    @Internal
    abstract Property<WebServer> getServer();

    @OutputFile
    abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void download() {
        // Use the server to download a file
        WebServer server = getServer().get();
        URI uri = server.getUri().resolve("somefile.zip");
        System.out.println(String.format("Downloading %s", uri));
    }
}
