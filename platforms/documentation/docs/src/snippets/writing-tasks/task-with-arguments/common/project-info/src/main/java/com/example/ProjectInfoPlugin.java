package com.example;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

class ProjectInfoPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getTasks().register("projectInfo", ProjectInfoTask.class, task -> {
            task.setGroup("help");
            task.setDescription("Displays current project info");
        });
    }
}
