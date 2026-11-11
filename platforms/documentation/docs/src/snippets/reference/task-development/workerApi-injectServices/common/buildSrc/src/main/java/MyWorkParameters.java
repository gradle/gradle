import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

// tag::work-parameters[]
public interface MyWorkParameters extends WorkParameters {
    Property<String> getMessage();
    RegularFileProperty getOutputFile();
    ConfigurableFileCollection getInputFiles();
    ListProperty<String> getFlags();
}
// end::work-parameters[]
