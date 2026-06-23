package org.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public class FileSizeDiffPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        // Register the extension
        FileSizeDiffExtension extension = project.getExtensions().create("diff", FileSizeDiffExtension.class);

        // Register and configure the task
        project.getTasks().register("fileSizeDiff", FileSizeDiffTask.class, task -> {
            task.getFile1().convention(extension.getFile1());
            task.getFile2().convention(extension.getFile2());
            task.getResultFile().convention(project.getLayout().getBuildDirectory().file("diff-result.txt"));
        });
    }
}
