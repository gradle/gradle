import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Collection;

/**
 * An immutable classpath.
 */
public interface ClassPath {
    boolean isEmpty();

    Collection<URI> getAsURIs();

    Collection<File> getAsFiles();

    Collection<URL> getAsURLs();

    URL[] getAsURLArray();

    ClassPath plus(Collection<File> classPath);
    
    ClassPath plus(ClassPath classPath);
}
