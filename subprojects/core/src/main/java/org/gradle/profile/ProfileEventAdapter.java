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
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.time.Clock;

/**
 * Adapts various events to build a {@link BuildProfile} model, and then notifies a {@link ReportGeneratingProfileListener} when the model is ready.
 */
public class ProfileEventAdapter implements BuildListener, ProjectEvaluationListener, TaskExecutionListener, DependencyResolutionListener, BuildCompletionListener {
    private final BuildStartedTime buildStartedTime;
    private final Clock clock;
    private final ProfileListener listener;
    private BuildProfile buildProfile;

    public ProfileEventAdapter(BuildStartedTime buildStartedTime, Clock clock, ProfileListener listener) {
        this.buildStartedTime = buildStartedTime;
        this.clock = clock;
        this.listener = listener;
    }

    // BuildListener
    public void buildStarted(Gradle gradle) {
        long now = clock.getCurrentTime();
        buildProfile = new BuildProfile(gradle.getStartParameter());
        buildProfile.setBuildStarted(now);
        buildProfile.setProfilingStarted(buildStartedTime.getStartTime());
    }

    public void settingsEvaluated(Settings settings) {
        buildProfile.setSettingsEvaluated(clock.getCurrentTime());
    }

    public void projectsLoaded(Gradle gradle) {
        buildProfile.setProjectsLoaded(clock.getCurrentTime());
    }

    public void projectsEvaluated(Gradle gradle) {
        buildProfile.setProjectsEvaluated(clock.getCurrentTime());
    }

    public void buildFinished(BuildResult result) {
        buildProfile.setSuccessful(result.getFailure() == null);
    }

    public void completed() {
        if (buildProfile != null) {
            buildProfile.setBuildFinished(clock.getCurrentTime());
            try {
                listener.buildFinished(buildProfile);
            } finally {
                buildProfile = null;
            }
        }
    }

    // ProjectEvaluationListener
    public void beforeEvaluate(Project project) {
        long now = clock.getCurrentTime();
        buildProfile.getProjectProfile(project.getPath()).getConfigurationOperation().setStart(now);
    }

    public void afterEvaluate(Project project, ProjectState state) {
        long now = clock.getCurrentTime();
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project.getPath());
        projectProfile.getConfigurationOperation().setFinish(now);
    }

    // TaskExecutionListener
    public void beforeExecute(Task task) {
        long now = clock.getCurrentTime();
        Project project = task.getProject();
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project.getPath());
        projectProfile.getTaskProfile(task.getPath()).setStart(now);
    }

    public void afterExecute(Task task, TaskState state) {
        long now = clock.getCurrentTime();
        Project project = task.getProject();
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project.getPath());
        TaskExecution taskExecution = projectProfile.getTaskProfile(task.getPath());
        taskExecution.setFinish(now);
        taskExecution.completed(state);
    }

    // DependencyResolutionListener
    public void beforeResolve(ResolvableDependencies dependencies) {
        long now = clock.getCurrentTime();
        buildProfile.getDependencySetProfile(dependencies.getPath()).setStart(now);
    }

    public void afterResolve(ResolvableDependencies dependencies) {
        long now = clock.getCurrentTime();
        buildProfile.getDependencySetProfile(dependencies.getPath()).setFinish(now);
    }
}

