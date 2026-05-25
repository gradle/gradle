import org.gradle.api.provider.Property;
import java.net.URI;

public interface Resource {
    Property<URI> getUri();
}
