import org.gradle.api.provider.Property;
import java.net.URI;
// tag::resource[]

public interface Resource {
    // Type must have a read-only 'name' property
    String getName();

    Property<URI> getUri();

    Property<String> getUserName();
}
// end::resource[]
