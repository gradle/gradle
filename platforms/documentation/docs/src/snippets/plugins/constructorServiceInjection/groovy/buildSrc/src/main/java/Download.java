import org.gradle.api.DefaultTask;
import org.gradle.workers.WorkerExecutor;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

// tag::download[]
public class Download extends DefaultTask {
    private final WorkerExecutor workerExecutor;

    // Inject an WorkerExecutor into the constructor
    @Inject
    public Download(WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor;
    }

    @TaskAction
    void run() {
        // ...use workerExecutor to run some work
    }
}
// end::download[]
