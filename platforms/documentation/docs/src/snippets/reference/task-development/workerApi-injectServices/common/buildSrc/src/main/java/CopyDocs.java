import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import javax.inject.Inject;

// tag::work-action[]
public abstract class CopyDocs implements WorkAction<CopyDocs.Parameters> {

    public interface Parameters extends WorkParameters {
        DirectoryProperty getSourceDirectory(); // <1>
        DirectoryProperty getOutputDirectory();
    }

    @Inject
    public abstract FileSystemOperations getFileSystemOperations(); // <2>

    @Override
    public void execute() {
        getFileSystemOperations().copy(spec -> {
            spec.from(getParameters().getSourceDirectory());
            spec.into(getParameters().getOutputDirectory());
        });
        System.out.println("Copied docs into " + getParameters().getOutputDirectory().get().getAsFile().getName());
    }
}
// end::work-action[]
