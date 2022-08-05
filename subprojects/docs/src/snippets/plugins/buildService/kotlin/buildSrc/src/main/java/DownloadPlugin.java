import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

public class DownloadPlugin implements Plugin<Project> {
    public void apply(Project project) {
        // Register the service
        Provider<WebServer> serviceProvider = project.getGradle().getSharedServices().registerIfAbsent("web", WebServer.class, spec -> {
            // Provide some parameters
            spec.getParameters().getPort().set(5005);
        });

        project.getTasks().register("download", Download.class, task -> {
            // Connect the service provider to the task
            task.getServer().set(serviceProvider);
            // Declare the association between the task and the service
            task.usesService(serviceProvider);
            task.getOutputFile().set(project.getLayout().getBuildDirectory().file("result.zip"));
        });
    }
}
