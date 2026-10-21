package org.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class HelloPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().register("hello", HelloTask.class, task ->
            task.getMessage().convention("Hello from HelloTask")
        );
    }
}
