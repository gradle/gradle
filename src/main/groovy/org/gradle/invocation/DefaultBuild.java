package org.gradle.invocation;

import org.gradle.api.invocation.Build;
import org.gradle.api.Project;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.util.GradleVersion;
import org.gradle.StartParameter;

import java.io.File;

public class DefaultBuild implements Build {
    private Project rootProject;
    private TaskExecutionGraph taskExecutionGraph;
    private final StartParameter startParameter;

    public DefaultBuild(Project rootProject, TaskExecutionGraph taskExecutionGraph, StartParameter startParameter) {
        this.rootProject = rootProject;
        this.taskExecutionGraph = taskExecutionGraph;
        this.startParameter = startParameter;
    }

    public String getGradleVersion() {
        return new GradleVersion().getVersion();
    }

    public File getGradleHomeDir() {
        return startParameter.getGradleHomeDir();
    }

    public File getGradleUserHomeDir() {
        return startParameter.getGradleUserHomeDir();
    }

    public Project getRootProject() {
        return rootProject;
    }

    public void setRootProject(Project rootProject) {
        this.rootProject = rootProject;
    }

    public TaskExecutionGraph getTaskExecutionGraph() {
        return taskExecutionGraph;
    }

    public void setTaskExecutionGraph(TaskExecutionGraph taskExecutionGraph) {
        this.taskExecutionGraph = taskExecutionGraph;
    }
}
