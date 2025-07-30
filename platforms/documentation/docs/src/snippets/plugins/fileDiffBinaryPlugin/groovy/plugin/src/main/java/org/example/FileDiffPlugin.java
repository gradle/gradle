package org.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

// tag::simple[]
public class FileDiffPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        System.out.println("FileDiff plugin applied");

        // Register the extension
        FileDiffExtension extension = project.getExtensions().create("fileDiff", FileDiffExtension.class);

        // Register the task
        TaskProvider<FileDiffTask> taskProvider = project.getTasks().register("fileDiff", FileDiffTask.class);

        // Configure the task
        taskProvider.configure( task -> {
            task.getFile1().set(extension.getFile1());
            task.getFile2().set(extension.getFile2());
        });
    }
}
// end::simple[]
