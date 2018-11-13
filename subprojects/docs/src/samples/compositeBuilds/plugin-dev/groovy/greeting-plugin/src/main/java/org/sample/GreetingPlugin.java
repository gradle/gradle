package org.sample;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.LinkedHashMap;

public class GreetingPlugin implements Plugin<Project> {

    public void apply(Project project) {
        GreetingExtension extension = project.getExtensions().create("greeting", GreetingExtension.class);
        GreetingTask task = project.getTasks().create("greeting", GreetingTask.class);
        project.afterEvaluate(p -> {
            task.setWho(extension.getWho());
        });
    }
}
