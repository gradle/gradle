package org.sample;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import java.util.LinkedHashMap;

public class GreetingPlugin implements Plugin<Project> {

    public void apply(Project project) {
        GreetingExtension extension = project.getExtensions().create("greeting", GreetingExtension.class);
        TaskProvider<GreetingTask> task = project.getTasks().register("greeting", GreetingTask.class);
        project.afterEvaluate(p -> {
            task.configure(t -> {
                t.setWho(extension.getWho());
            });
        });
    }
}
