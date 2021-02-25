import org.gradle.api.NamedDomainObjectContainer;

// tag::download-extension[]
public interface DownloadExtension {
    NamedDomainObjectContainer<Resource> getResources();
}
// end::download-extension[]
