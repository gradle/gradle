package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Registers a test task to demonstrate the {@code TestEventReporter} API.
 */
public abstract class CustomTestPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().register("customTest", CustomTest.class, task -> {
            task.setGroup("verification");
            task.setDescription("Runs the first custom test suite.");
            task.getExtensions().getExtraProperties().set("idea.internal.test", true);
        });
    }
}
