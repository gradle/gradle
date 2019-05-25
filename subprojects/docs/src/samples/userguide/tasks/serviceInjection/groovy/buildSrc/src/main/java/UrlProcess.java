import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.DefaultTask ;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

public class UrlProcess extends DefaultTask {
    // Inject an ObjectFactory into the constructor
    @Inject
    public UrlProcess(ObjectFactory objectFactory) {
        // Use the factory ...
    }

    // Alternatively, use a getter method with a dummy implementation
    @Inject
    public WorkerExecutor getWorkerExecutor() {
        // Method body is ignored
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void run() {
        WorkerExecutor workerExecutor = getWorkerExecutor();
        // Use the executor ....
    }
}