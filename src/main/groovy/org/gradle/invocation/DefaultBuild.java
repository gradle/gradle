package org.gradle.invocation;

import org.gradle.StartParameter;
import org.gradle.execution.Dag;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.project.DefaultProjectRegistry;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.GradleVersion;

import java.io.File;

public class DefaultBuild implements BuildInternal {
    private ProjectInternal rootProject;
    private ProjectInternal currentProject;
    private Dag taskExecutionGraph;
    private StartParameter startParameter;
    private ClassLoader buildScriptClassLoader;
    private DefaultProjectRegistry projectRegistry;

    public DefaultBuild(StartParameter startParameter, ClassLoader buildScriptClassLoader) {
        this.startParameter = startParameter;
        this.buildScriptClassLoader = buildScriptClassLoader;
        this.projectRegistry = new DefaultProjectRegistry();
        this.taskExecutionGraph = new Dag();
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

    public Dag getTaskGraph() {
        return taskExecutionGraph;
    }

    public void setTaskExecutionGraph(Dag taskExecutionGraph) {
        this.taskExecutionGraph = taskExecutionGraph;
    }

    public IProjectRegistry getProjectRegistry() {
        return projectRegistry;
    }

    public ClassLoader getBuildScriptClassLoader() {
        return buildScriptClassLoader;
    }
}
