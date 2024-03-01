import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.net.URI;

public abstract class Download extends DefaultTask {

    public static abstract class DownloadWorkAction implements WorkAction<DownloadWorkAction.Parameters> {
        interface Parameters extends WorkParameters {
            // This property provides access to the service instance from the work action
            abstract Property<WebServer> getServer();
        }

        @Override
        public void execute() {
            // Use the server to download a file
            WebServer server = getParameters().getServer().get();
            URI uri = server.getUri().resolve("somefile.zip");
            System.out.println(String.format("Downloading %s", uri));
        }
    }

    @Inject
    abstract public WorkerExecutor getWorkerExecutor();

    // This property provides access to the service instance from the task
    @ServiceReference("web")
    abstract Property<WebServer> getServer();

    @TaskAction
    public void download() {
        WorkQueue workQueue = getWorkerExecutor().noIsolation();
        workQueue.submit(DownloadWorkAction.class, parameter -> {
            parameter.getServer().set(getServer());
        });
    }
}
