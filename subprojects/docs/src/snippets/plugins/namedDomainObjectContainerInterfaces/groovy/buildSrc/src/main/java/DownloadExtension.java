import java.net.URI;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectContainer;

public interface DownloadExtension {

    // A container of `Resource` objects
    NamedDomainObjectContainer<Resource> getResources();
}
