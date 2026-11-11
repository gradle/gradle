import org.gradle.api.Named;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

public interface Server extends Named {
    @Input
    @Override
    String getName();

    @Input
    Property<String> getUrl();
}
