package org.gradle.invocation;

import org.gradle.StartParameter;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.project.DefaultProjectRegistry;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.GradleVersion;

import java.io.File;

public class DefaultBuild implements BuildInternal {
    private ProjectInternal rootProject;
    private ProjectInternal currentProject;
    private TaskExecutionGraph taskExecutionGraph;
    private StartParameter startParameter;
    private ClassLoader buildScriptClassLoader;
    private DefaultProjectRegistry projectRegistry;

    public DefaultBuild(TaskExecutionGraph taskExecutionGraph, StartParameter startParameter,
                        ClassLoader buildScriptClassLoader) {
        this.taskExecutionGraph = taskExecutionGraph;
        this.startParameter = startParameter;
        this.buildScriptClassLoader = buildScriptClassLoader;
        this.projectRegistry = new DefaultProjectRegistry();
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

    public StartParameter getStartParameter() {
        return startParameter;
    }

    public ProjectInternal getRootProject() {
        return rootProject;
    }

    public void setRootProject(ProjectInternal rootProject) {
        this.rootProject = rootProject;
    }

    public ProjectInternal getCurrentProject() {
        return currentProject;
    }

    public void setCurrentProject(ProjectInternal currentProject) {
        this.currentProject = currentProject;
    }

    public TaskExecutionGraph getTaskGraph() {
        return taskExecutionGraph;
    }

    public void setTaskExecutionGraph(TaskExecutionGraph taskExecutionGraph) {
        this.taskExecutionGraph = taskExecutionGraph;
    }

    public IProjectRegistry getProjectRegistry() {
        return projectRegistry;
    }

    public ClassLoader getBuildScriptClassLoader() {
        return buildScriptClassLoader;
    }
}
