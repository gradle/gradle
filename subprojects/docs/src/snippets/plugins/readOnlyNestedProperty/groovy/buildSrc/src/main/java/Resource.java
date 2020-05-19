import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
// tag::resource[]

public interface Resource {
    @Input
    Property<String> getHostName();
    @Input
    Property<String> getPath();
}
// end::resource[]
