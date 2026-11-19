import org.gradle.api.provider.MapProperty;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.util.Map;
import java.util.TreeMap;

// tag::work-action[]
public abstract class PrintServers implements WorkAction<PrintServers.Parameters> {

    public interface Parameters extends WorkParameters {
        MapProperty<String, String> getUrls(); // <1>
    }

    @Override
    public void execute() {
        Map<String, String> urls = new TreeMap<>(getParameters().getUrls().get());
        urls.forEach((name, url) -> System.out.println(name + " -> " + url));
    }
}
// end::work-action[]
