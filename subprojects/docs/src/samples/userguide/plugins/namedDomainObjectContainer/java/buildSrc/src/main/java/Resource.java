
import java.net.URI;

public class Resource {
    private final String name;
    private URI uri;
    private String userName;

    public Resource(String name) {
        this.name = name;
    }

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
