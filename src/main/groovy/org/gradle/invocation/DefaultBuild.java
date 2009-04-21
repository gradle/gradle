package org.gradle.invocation;

import org.gradle.StartParameter;
import org.gradle.execution.DefaultTaskExecuter;
import org.gradle.execution.TaskExecuter;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.project.DefaultProjectRegistry;
import org.gradle.api.internal.project.IProjectRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.util.GradleVersion;
import org.gradle.util.ListenerBroadcast;

import java.io.File;

import groovy.lang.Closure;

public class DefaultBuild implements BuildInternal {
    private ProjectInternal rootProject;
    private ProjectInternal defaultProject;
    private TaskExecuter taskGraph;
    private StartParameter startParameter;
    private ClassLoader buildScriptClassLoader;
    private InternalRepository internalRepository;
    private DefaultProjectRegistry<ProjectInternal> projectRegistry;
    private final ListenerBroadcast<ProjectEvaluationListener> projectEvaluationListenerBroadcast
            = new ListenerBroadcast<ProjectEvaluationListener>(ProjectEvaluationListener.class);

    public DefaultBuild(StartParameter startParameter, ClassLoader buildScriptClassLoader, InternalRepository internalRepository) {
        this.startParameter = startParameter;
        this.buildScriptClassLoader = buildScriptClassLoader;
        this.internalRepository = internalRepository;
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
        return defaultProject;
    }

    public void setDefaultProject(ProjectInternal defaultProject) {
        this.defaultProject = defaultProject;
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

    public InternalRepository getInternalRepository() {
        return internalRepository;
    }

    public void setInternalRepository(InternalRepository internalRepository) {
        this.internalRepository = internalRepository;
    }

    public ProjectEvaluationListener addProjectEvaluationListener(ProjectEvaluationListener listener) {
        projectEvaluationListenerBroadcast.add(listener);
        return listener;
    }

    public void removeProjectEvaluationListener(ProjectEvaluationListener listener) {
        projectEvaluationListenerBroadcast.remove(listener);
    }

    public void beforeProject(Closure closure) {
        projectEvaluationListenerBroadcast.add("beforeEvaluate", closure);
    }

    public void afterProject(Closure closure) {
        projectEvaluationListenerBroadcast.add("afterEvaluate", closure);
    }

    public ProjectEvaluationListener getProjectEvaluationBroadcaster() {
        return projectEvaluationListenerBroadcast.getSource();
    }
}
