import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.build.event.BuildEventsListenerRegistry;

import javax.inject.Inject;

public abstract class TaskEventsPlugin implements Plugin<Project> {
    @Inject
    public abstract BuildEventsListenerRegistry getEventsListenerRegistry(); // <1> Use service injection to obtain an instance of the `BuildEventsListenerRegistry`.

    @Override
    public void apply(Project project) {
        Provider<TaskEventsService> serviceProvider =
            project.getGradle().getSharedServices().registerIfAbsent(
                "taskEvents", TaskEventsService.class, spec -> {}); // <2> Register the build service as usual.

        getEventsListenerRegistry().onTaskCompletion(serviceProvider); // <3> Use the service `Provider` to subscribe to the build service to build events.
    }
}
