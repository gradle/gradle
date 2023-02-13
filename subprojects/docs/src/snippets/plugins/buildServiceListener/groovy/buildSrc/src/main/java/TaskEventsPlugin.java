import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.build.event.BuildEventsListenerRegistry;

import javax.inject.Inject;

public abstract class TaskEventsPlugin implements Plugin<Project> {
    @Inject
    public abstract BuildEventsListenerRegistry getEventsListenerRegistry(); // <1>

    @Override
    public void apply(Project project) {
        Provider<TaskEventsService> serviceProvider =
            project.getGradle().getSharedServices().registerIfAbsent(
                "taskEvents", TaskEventsService.class, spec -> {}); // <2>

        getEventsListenerRegistry().onTaskCompletion(serviceProvider); // <3>
    }
}
