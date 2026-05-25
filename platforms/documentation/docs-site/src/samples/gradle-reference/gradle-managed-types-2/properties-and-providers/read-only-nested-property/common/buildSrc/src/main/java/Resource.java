import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

public interface Resource {
    @Input
    Property<String> getHostName();
    @Input
    Property<String> getPath();
}
