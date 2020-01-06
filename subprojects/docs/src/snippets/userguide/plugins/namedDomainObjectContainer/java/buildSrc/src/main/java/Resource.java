
import java.net.URI;
// tag::resource[]

public class Resource {
    private final String name;
    private URI uri;
    private String userName;

    // Type must have a public constructor that takes the element name as a parameter
    public Resource(String name) {
        this.name = name;
    }

    // Type must have a 'name' property, which should be read-only
    public String getName() {
        return name;
    }

    public URI getUri() {
        return uri;
    }
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getUserName() {
        return userName;
    }
    public void setUserName(String userName) {
        this.userName = userName;
    }
}
// end::resource[]
