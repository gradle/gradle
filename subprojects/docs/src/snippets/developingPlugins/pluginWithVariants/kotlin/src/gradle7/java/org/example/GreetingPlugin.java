package org.example;

import org.gradle.api.Project;
import org.gradle.api.Plugin;

public class GreetingPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        target.getTasks().register("hello", GreetingTask.class);
    }
}
