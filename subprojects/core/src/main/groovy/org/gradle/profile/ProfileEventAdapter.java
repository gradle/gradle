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
import org.gradle.initialization.BuildCompletionListener;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.TimeProvider;

/**
 * Adapts various events to build a {@link BuildProfile} model, and then notifies a {@link ReportGeneratingProfileListener} when the model is ready.
 */
public class ProfileEventAdapter implements BuildListener, ProjectEvaluationListener, TaskExecutionListener, DependencyResolutionListener, BuildCompletionListener {
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
        long now = timeProvider.getCurrentTime();
        buildProfile = new BuildProfile(gradle.getStartParameter());
        buildProfile.setBuildStarted(now);
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
        buildProfile.setSuccessful(result.getFailure() == null);
    }

    public void completed() {
        buildProfile.setBuildFinished(timeProvider.getCurrentTime());
        try {
            listener.buildFinished(buildProfile);
        } finally {
            buildProfile = null;
        }
    }

    // ProjectEvaluationListener
    public void beforeEvaluate(Project project) {
        long now = timeProvider.getCurrentTime();
        buildProfile.getProjectProfile(project.getPath()).getConfigurationOperation().setStart(now);
    }

    public void afterEvaluate(Project project, ProjectState state) {
        long now = timeProvider.getCurrentTime();
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project.getPath());
        projectProfile.getConfigurationOperation().setFinish(now);
    }

    // TaskExecutionListener
    public void beforeExecute(Task task) {
        long now = timeProvider.getCurrentTime();
        Project project = task.getProject();
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project.getPath());
        projectProfile.getTaskProfile(task.getPath()).setStart(now);
    }

    public void afterExecute(Task task, TaskState state) {
        long now = timeProvider.getCurrentTime();
        Project project = task.getProject();
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project.getPath());
        TaskExecution taskExecution = projectProfile.getTaskProfile(task.getPath());
        taskExecution.setFinish(now);
        taskExecution.completed(state);
    }

    // DependencyResolutionListener
    public void beforeResolve(ResolvableDependencies dependencies) {
        long now = timeProvider.getCurrentTime();
        buildProfile.getDependencySetProfile(dependencies.getPath()).setStart(now);
    }

    public void afterResolve(ResolvableDependencies dependencies) {
        long now = timeProvider.getCurrentTime();
        buildProfile.getDependencySetProfile(dependencies.getPath()).setFinish(now);
    }
}

