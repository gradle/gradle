import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;

// tag::task[]
public abstract class CopyDocsTask extends DefaultTask {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    public abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void copyDocs() {
        getWorkerExecutor().processIsolation().submit(CopyDocs.class, parameters -> {
            parameters.getSourceDirectory().set(getSourceDirectory()); // <1>
            parameters.getOutputDirectory().set(getOutputDirectory());
        });
    }
}
// end::task[]
