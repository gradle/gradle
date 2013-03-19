/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.profile;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.api.Task;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskState;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.TimeProvider;

/**
 * Adapts various events to build a {@link BuildProfile} model, and then notifies a {@link ReportGeneratingProfileListener} when the model is ready.
 */
public class ProfileEventAdapter implements BuildListener, ProjectEvaluationListener, TaskExecutionListener, DependencyResolutionListener {
    private final BuildRequestMetaData buildMetaData;
    private final TimeProvider timeProvider;
    private final ProfileListener listener;
    private BuildProfile buildProfile;

    public ProfileEventAdapter(BuildRequestMetaData buildMetaData, TimeProvider timeProvider, ProfileListener listener) {
        this.buildMetaData = buildMetaData;
        this.timeProvider = timeProvider;
        this.listener = listener;
    }

    // BuildListener
    public void buildStarted(Gradle gradle) {
        buildProfile = new BuildProfile(gradle.getStartParameter());
        buildProfile.setBuildStarted(timeProvider.getCurrentTime());
        buildProfile.setProfilingStarted(buildMetaData.getBuildTimeClock().getStartTime());
    }

    public void settingsEvaluated(Settings settings) {
        buildProfile.setSettingsEvaluated(timeProvider.getCurrentTime());
    }

    public void projectsLoaded(Gradle gradle) {
        buildProfile.setProjectsLoaded(timeProvider.getCurrentTime());
    }

    public void projectsEvaluated(Gradle gradle) {
        buildProfile.setProjectsEvaluated(timeProvider.getCurrentTime());
    }

    public void buildFinished(BuildResult result) {
        buildProfile.setBuildFinished(timeProvider.getCurrentTime());
        buildProfile.setSuccessful(result.getFailure() == null);
        try {
            listener.buildFinished(buildProfile);
        } finally {
            buildProfile = null;
        }
    }

    // ProjectEvaluationListener
    public void beforeEvaluate(Project project) {
        buildProfile.getProjectProfile(project.getPath()).getConfigurationOperation().setStart(System.currentTimeMillis());
    }

    public void afterEvaluate(Project project, ProjectState state) {
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project.getPath());
        projectProfile.getConfigurationOperation().setFinish(timeProvider.getCurrentTime());
        projectProfile.setState(state);
    }

    // TaskExecutionListener
    public void beforeExecute(Task task) {
        Project project = task.getProject();
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project.getPath());
        projectProfile.getTaskProfile(task).setStart(timeProvider.getCurrentTime());
    }

    public void afterExecute(Task task, TaskState state) {
        Project project = task.getProject();
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project.getPath());
        TaskExecution taskExecution = projectProfile.getTaskProfile(task);
        taskExecution.setFinish(timeProvider.getCurrentTime());
        taskExecution.setState(state);
    }

    // DependencyResolutionListener
    public void beforeResolve(ResolvableDependencies dependencies) {
        DependencyResolveProfile profile = buildProfile.getDependencySetProfile(dependencies.getPath());
        profile.setStart(timeProvider.getCurrentTime());
    }

    public void afterResolve(ResolvableDependencies dependencies) {
        DependencyResolveProfile profile = buildProfile.getDependencySetProfile(dependencies.getPath());
        profile.setFinish(timeProvider.getCurrentTime());
    }
}

