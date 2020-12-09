import java.net.URI;
import org.gradle.api.Named;
// tag::resource[]

public interface Resource extends Named {

    URI getUri();
    void setUri(URI uri);

    String getUserName();
    void setUserName(String userName);
}
// end::resource[]
