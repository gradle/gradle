import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;

// tag::task[]
public abstract class PrintServersTask extends DefaultTask {

    @Nested
    public abstract NamedDomainObjectContainer<Server> getServers();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void printServers() {
        getWorkerExecutor().processIsolation().submit(PrintServers.class, parameters -> {
            for (Server server : getServers()) {
                parameters.getUrls().put(server.getName(), server.getUrl()); // <1>
            }
        });
    }
}
// end::task[]
