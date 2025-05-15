package org.example;

import org.gradle.api.Project;
import org.gradle.api.Plugin;

public abstract class MyPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getTasks().register("myTask", MyTask.class);
    }
}
