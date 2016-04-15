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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.gradle.util.GradleVersion;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data bindings for rendering the profile report in JSON
 */
public class ProfileAsJsonReportBindings {
    // Note: SimpleDateFormat.format() should not be called concurrently. We should be single threaded here, so no problem.
    private static final SimpleDateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private final BuildProfile buildProfile;
    private final CompositeOperation<Operation> profiledProjectConfiguration;
    private final CompositeOperation<ContinuousOperation> profiledDependencyResolution;

    public ProfileAsJsonReportBindings(BuildProfile buildProfile) {
        this.buildProfile = buildProfile;
        this.profiledProjectConfiguration = buildProfile.getProjectConfiguration();
        this.profiledDependencyResolution = buildProfile.getDependencySets();

    }

    public class DurationBindings {
        private final long duration;

        public DurationBindings(long duration) {
            this.duration = duration;
        }

        @JsonProperty("duration")
        long getDuration() {
            return duration;
        }
    }

    public class ProjectConfigurationBindings extends DurationBindings {
        public ProjectConfigurationBindings() {
            super(profiledProjectConfiguration.getElapsedTime());
        }

        @JsonProperty("projects")
        public Map<String, DurationBindings> getProjectConfigurations() {
            final Map<String, DurationBindings> projectConfigurations = new LinkedHashMap<String, DurationBindings>();

            for (Operation operation : profiledProjectConfiguration) {
                projectConfigurations.put(operation.getDescription(), new DurationBindings(operation.getElapsedTime()));
            }

            return projectConfigurations;
        }
    }

    public class DependencyResolutionBindings extends DurationBindings {
        public DependencyResolutionBindings() {
            super(profiledDependencyResolution.getElapsedTime());
        }

        @JsonProperty("dependencies")
        public Map<String, DurationBindings> getDependencyResolutions() {
            final Map<String, DurationBindings> dependencyResolutions = new LinkedHashMap<String, DurationBindings>();

            for (Operation operation : profiledDependencyResolution) {
                dependencyResolutions.put(operation.getDescription(), new DurationBindings(operation.getElapsedTime()));
            }

            return dependencyResolutions;
        }
    }

    public class DurationStatusBindings extends DurationBindings {
        private final String status;

        public DurationStatusBindings(long duration, String status) {
            super(duration);
            this.status = status;
        }

        @JsonProperty("status")
        public String getStatus() {
            return status;
        }
    }

    public class TaskExecutionBindings extends DurationBindings {
        private final ProjectProfile projectProfile;

        public TaskExecutionBindings(ProjectProfile projectProfile) {
            super(projectProfile.getElapsedTime());
            this.projectProfile = projectProfile;
        }

        @JsonProperty("tasks")
        public Map<String, DurationStatusBindings> getTaskExecutions() {
            final Map<String, DurationStatusBindings> taskExecutions = new LinkedHashMap<String, DurationStatusBindings>();

            for (TaskExecution taskExecution : projectProfile.getTasks()) {
                taskExecutions.put(taskExecution.getPath(), new DurationStatusBindings(taskExecution.getElapsedTime(), taskExecution.getStatus()));
            }

            return taskExecutions;
        }
    }

    public class ProjectExecutionBindings extends DurationBindings {
        public ProjectExecutionBindings() {
            super(buildProfile.getElapsedTotalExecutionTime());
        }

        @JsonProperty("projects")
        public Map<String, TaskExecutionBindings> getProjectExecutions() {
            final Map<String, TaskExecutionBindings> projectExecutions = new LinkedHashMap<String, TaskExecutionBindings>();

            for (ProjectProfile projectProfile : buildProfile.getProjects()) {
                projectExecutions.put(projectProfile.getPath(), new TaskExecutionBindings(projectProfile));
            }

            return projectExecutions;
        }
    }

    public class BuildBindings {

        @JsonProperty("total_build_time")
        public DurationBindings getTotalBuildTime() {
            return new DurationBindings(buildProfile.getElapsedTotal());
        }

        @JsonProperty("startup")
        public DurationBindings getStartup() {
            return new DurationBindings(buildProfile.getElapsedStartup());
        }

        @JsonProperty("settings_and_buildsrc")
        public DurationBindings getSettingsAndBuildsrc() {
            return new DurationBindings(buildProfile.getElapsedSettings());
        }

        @JsonProperty("loading_projects")
        public DurationBindings getLoadingProjects() {
            return new DurationBindings(buildProfile.getElapsedProjectsLoading());
        }

        @JsonProperty("configuring_projects")
        public ProjectConfigurationBindings getConfiguringProjects() {
            return new ProjectConfigurationBindings();
        }

        @JsonProperty("resolving_dependencies")
        public DependencyResolutionBindings getResolvingDependencies() {
            return new DependencyResolutionBindings();
        }

        @JsonProperty("executing_projects")
        public ProjectExecutionBindings getExecutingProjects() {
            return new ProjectExecutionBindings();
        }
    }

    @JsonProperty("title")
    public String getTitle() {
        return "Profile report";
    }

    @JsonProperty("profiled_build")
    public String getProfiledBuild() {
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

        return tasks;
    }

    @JsonProperty("started_on")
    String getStartedOn() {
        return ISO_DATE_FORMAT.format(buildProfile.getBuildStarted());
    }

    @JsonProperty("generated_by")
    String getGeneratedBy() {
        return String.format("Gradle %s", GradleVersion.current().getVersion());
    }

    @JsonProperty("generated_at")
    String getGeneratedAt() {
        return ISO_DATE_FORMAT.format(new Date());
    }

    @JsonProperty("measurements")
    public BuildBindings getMeasurements() {
        return new BuildBindings();
    }
}
