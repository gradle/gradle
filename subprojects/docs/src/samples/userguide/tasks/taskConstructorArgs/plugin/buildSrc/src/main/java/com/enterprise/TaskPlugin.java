package com.enterprise;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;
import javax.inject.Inject;

public class TaskPlugin implements Plugin<Project> {
    public static class CustomTask extends DefaultTask {
        private final String message;
        private final int number;

        @Inject
        public CustomTask(String message, int number) {
            this.message = message;
            this.number = number;
        }

        @TaskAction
        public void printIt() {
            System.out.println(message + " " + number);
        }
    }

    @Override
// START SNIPPET task-container-create
    public void apply(Project project) {
        project.getTasks().create("myTask", CustomTask.class, "hello", 42);
    }
// END SNIPPET task-container-create
}
