package com.enterprise;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;
import javax.inject.Inject;

public class TaskPlugin implements Plugin<Project> {
    public static class CustomTask extends DefaultTask {
        private final int number;
        private final WorkerExecutor executor;

        @Inject
        public CustomTask(int number, WorkerExecutor executor) {
            this.number = number;
            this.executor = executor;
        }

        @TaskAction
        public void printIt() {
            System.out.println(executor != null ? "got it " + number : number + " NOT IT");
        }
    }

    @Override
    public void apply(Project project) {
        project.getTasks().create("myTask", CustomTask.class, 123);
    }
}
