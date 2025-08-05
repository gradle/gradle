package org.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public class FileSizeDiffPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Register the extension
        FileSizeDiffExtension extension = project.getExtensions().create("fileSizeDiff", FileSizeDiffExtension.class);

        // Register the task
        TaskProvider<FileSizeDiffTask> taskProvider = project.getTasks().register("fileSizeDiff", FileSizeDiffTask.class);

        // Configure the task
        taskProvider.configure( task -> {
            task.getFile1().set(extension.getFile1());
            task.getFile2().set(extension.getFile2());
        });
    }
}
