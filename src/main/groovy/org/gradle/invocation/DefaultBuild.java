package org.gradle.invocation;

import org.gradle.api.invocation.Build;
import org.gradle.api.Project;
import org.gradle.api.execution.TaskExecutionGraph;

public class DefaultBuild implements Build {
    private Project rootProject;
    private TaskExecutionGraph taskExecutionGraph;

    public DefaultBuild(Project rootProject, TaskExecutionGraph taskExecutionGraph) {
        this.rootProject = rootProject;
        this.taskExecutionGraph = taskExecutionGraph;
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
