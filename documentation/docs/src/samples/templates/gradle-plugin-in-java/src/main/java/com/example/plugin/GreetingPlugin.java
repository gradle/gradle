package com.example.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GreetingPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getTasks().register("greet", task -> {
            task.doLast(s -> System.out.println("Hello from plugin 'com.example.plugin.greeting'"));
        });
    }
}
