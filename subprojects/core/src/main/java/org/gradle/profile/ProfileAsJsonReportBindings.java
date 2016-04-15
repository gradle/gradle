/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.util.GradleVersion;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data bindings for rendering the profile report in JSON
 */
public class ProfileAsJsonReportBindings {
    public final String title;
    public final String profiledBuild;
    public final String startedOn;
    public final String generatedBy;
    public final String generatedAt;
    public final BuildBindings measurements;

    public ProfileAsJsonReportBindings(BuildProfile buildProfile) {
        final SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        this.title = "Profile report";

        StringBuilder sb = new StringBuilder();
        for (String name : buildProfile.getStartParameter().getExcludedTaskNames()) {
            sb.append("-x ");
            sb.append(name);
            sb.append(" ");
        }
        for (String name : buildProfile.getStartParameter().getTaskNames()) {
            sb.append(name);
            sb.append(" ");
        }
        String tasks = sb.toString().trim();
        if (tasks.length() == 0) {
            tasks = "(no tasks specified)";
        }
        this.profiledBuild = tasks;
        this.startedOn = isoDateFormat.format(buildProfile.getBuildStarted());
        this.generatedBy = String.format("Gradle %s", GradleVersion.current().getVersion());
        this.generatedAt = isoDateFormat.format(new Date());
        this.measurements = new BuildBindings(buildProfile);
    }

    public static class DurationBindings {
        public final long duration;

        public DurationBindings(long duration) {
            this.duration = duration;
        }
    }

    public static class ProjectConfigurationBindings {
        public final long duration;
        public final Map<String, DurationBindings> projects;

        public ProjectConfigurationBindings(CompositeOperation<Operation> profiledProjectConfiguration) {
            this.duration = profiledProjectConfiguration.getElapsedTime();

            final Map<String, DurationBindings> projectConfigurations = new LinkedHashMap<String, DurationBindings>();
            for (Operation operation : profiledProjectConfiguration) {
                projectConfigurations.put(operation.getDescription(), new DurationBindings(operation.getElapsedTime()));
            }

            this.projects = projectConfigurations;
        }
    }

    public static class DependencyResolutionBindings {
        public final long duration;
        public final Map<String, DurationBindings> dependencies;

        public DependencyResolutionBindings(CompositeOperation<ContinuousOperation> profiledDependencyResolution) {
            this.duration = profiledDependencyResolution.getElapsedTime();

            final Map<String, DurationBindings> dependencyResolutions = new LinkedHashMap<String, DurationBindings>();
            for (Operation operation : profiledDependencyResolution) {
                dependencyResolutions.put(operation.getDescription(), new DurationBindings(operation.getElapsedTime()));
            }

            this.dependencies = dependencyResolutions;
        }
    }

    public static class DurationStatusBindings {
        public final long duration;
        public final String status;

        public DurationStatusBindings(long duration, String status) {
            this.duration = duration;
            this.status = status;
        }
    }

    public static class TaskExecutionBindings {
        public final long duration;
        public final Map<String, DurationStatusBindings> tasks;

        public TaskExecutionBindings(ProjectProfile projectProfile) {
            this.duration = projectProfile.getElapsedTime();

            final Map<String, DurationStatusBindings> taskExecutions = new LinkedHashMap<String, DurationStatusBindings>();
            for (TaskExecution taskExecution : projectProfile.getTasks()) {
                taskExecutions.put(taskExecution.getPath(), new DurationStatusBindings(taskExecution.getElapsedTime(), taskExecution.getStatus()));
            }

            this.tasks = taskExecutions;
        }
    }

    public static class ProjectExecutionBindings {
        public final long duration;
        public final Map<String, TaskExecutionBindings> projects;

        public ProjectExecutionBindings(BuildProfile buildProfile) {
            this.duration = buildProfile.getElapsedTotalExecutionTime();

            final Map<String, TaskExecutionBindings> projectExecutions = new LinkedHashMap<String, TaskExecutionBindings>();
            for (ProjectProfile projectProfile : buildProfile.getProjects()) {
                projectExecutions.put(projectProfile.getPath(), new TaskExecutionBindings(projectProfile));
            }

            this.projects = projectExecutions;
        }
    }

    public static class BuildBindings {
       public final DurationBindings totalBuildTime;
       public final DurationBindings startup;
       public final DurationBindings settingsAndBuildsrc;
       public final DurationBindings loadingProjects;
       public final ProjectConfigurationBindings configuringProjects;
       public final DependencyResolutionBindings resolvingDependencies;
       public final ProjectExecutionBindings executingProjects;

        public BuildBindings(BuildProfile buildProfile) {
            this.totalBuildTime = new DurationBindings(buildProfile.getElapsedTotal());
            this.startup = new DurationBindings(buildProfile.getElapsedStartup());
            this.settingsAndBuildsrc = new DurationBindings(buildProfile.getElapsedSettings());
            this.loadingProjects = new DurationBindings(buildProfile.getElapsedProjectsLoading());
            this.configuringProjects = new ProjectConfigurationBindings(buildProfile.getProjectConfiguration());
            this.resolvingDependencies = new DependencyResolutionBindings(buildProfile.getDependencySets());
            this.executingProjects = new ProjectExecutionBindings(buildProfile);
        }
    }
}
