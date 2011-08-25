/*
 * Copyright 2009 the original author or authors.
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ProfileListener implements BuildListener, ProjectEvaluationListener, TaskExecutionListener, DependencyResolutionListener {
    private BuildProfile buildProfile;
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    private long profileStarted;

    public ProfileListener(long profileStarted) {
        this.profileStarted = profileStarted;
    }

    // BuildListener
    public void buildStarted(Gradle gradle) {
        buildProfile = new BuildProfile(gradle);
        buildProfile.setBuildStarted(System.currentTimeMillis());
        buildProfile.setProfilingStarted(profileStarted);
    }

    public void settingsEvaluated(Settings settings) {
        buildProfile.setSettingsEvaluated(System.currentTimeMillis());
    }

    public void projectsLoaded(Gradle gradle) {
        buildProfile.setProjectsLoaded(System.currentTimeMillis());
    }

    public void projectsEvaluated(Gradle gradle) {
        buildProfile.setProjectsEvaluated(System.currentTimeMillis());
    }

    public void buildFinished(BuildResult result) {
        buildProfile.setBuildFinished(System.currentTimeMillis());

        ProfileReportRenderer renderer = new ProfileReportRenderer();
        File file = new File(result.getGradle().getRootProject().getBuildDir(), "reports/profile/profile-" + FILE_DATE_FORMAT.format(new Date(profileStarted)) + ".html");
        renderer.writeTo(buildProfile, file);
    }

    // ProjectEvaluationListener
    public void beforeEvaluate(Project project) {
        buildProfile.getProjectProfile(project).getEvaluation().setStart(System.currentTimeMillis());
    }

    public void afterEvaluate(Project project, ProjectState state) {
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project);
        projectProfile.getEvaluation().setFinish(System.currentTimeMillis());
        projectProfile.setState(state);
    }

    // TaskExecutionListener
    public void beforeExecute(Task task) {
        Project project = task.getProject();
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project);
        projectProfile.getTaskProfile(task).setStart(System.currentTimeMillis());
    }

    public void afterExecute(Task task, TaskState state) {
        Project project = task.getProject();
        ProjectProfile projectProfile = buildProfile.getProjectProfile(project);
        TaskExecution taskExecution = projectProfile.getTaskProfile(task);
        taskExecution.setFinish(System.currentTimeMillis());
        taskExecution.setState(state);
    }

    // DependencyResolutionListener
    public void beforeResolve(ResolvableDependencies dependencies) {
        DependencyResolveProfile profile = buildProfile.getDependencySetProfile(dependencies);
        profile.setStart(System.currentTimeMillis());
    }

    public void afterResolve(ResolvableDependencies dependencies) {
        DependencyResolveProfile profile = buildProfile.getDependencySetProfile(dependencies);
        profile.setFinish(System.currentTimeMillis());
    }
}

