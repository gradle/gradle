import org.gradle.api.provider.Property;
import java.net.URI;
// tag::resource[]

public interface Resource {
    Property<URI> getUri();
}
// end::resource[]
