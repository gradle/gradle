package org.gradle.invocation;

import org.gradle.StartParameter;
import org.gradle.execution.Dag;
import org.gradle.execution.DefaultTaskExecuter;
import org.gradle.execution.TaskExecuter;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.project.DefaultProjectRegistry;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.Task;
import org.gradle.util.GradleVersion;

import java.io.File;

public class DefaultBuild implements BuildInternal {
    private ProjectInternal rootProject;
    private ProjectInternal currentProject;
    private TaskExecuter taskGraph;
    private StartParameter startParameter;
    private ClassLoader buildScriptClassLoader;
    private DefaultProjectRegistry<ProjectInternal> projectRegistry;

    public DefaultBuild(StartParameter startParameter, ClassLoader buildScriptClassLoader) {
        this.startParameter = startParameter;
        this.buildScriptClassLoader = buildScriptClassLoader;
        this.projectRegistry = new DefaultProjectRegistry<ProjectInternal>();
        this.taskGraph = new DefaultTaskExecuter();
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

    public ProjectInternal getDefaultProject() {
        return currentProject;
    }

    public void setCurrentProject(ProjectInternal currentProject) {
        this.currentProject = currentProject;
    }

    public TaskExecuter getTaskGraph() {
        return taskGraph;
    }

    public void setTaskGraph(TaskExecuter taskGraph) {
        this.taskGraph = taskGraph;
    }

    public IProjectRegistry<ProjectInternal> getProjectRegistry() {
        return projectRegistry;
    }

    public ClassLoader getBuildScriptClassLoader() {
        return buildScriptClassLoader;
    }
}
