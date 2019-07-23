import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

// tag::host-and-path[]

public interface HostAndPath {
    @Input
    Property<String> getHostName();
    @Input
    Property<String> getPath();
}
// end::host-and-path[]
