/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.util.CollectionUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root container for profile information about a build.  This includes summary
 * information about the overall build timing and collection of project specific
 * information.  All timing information is stored as milliseconds since epoch times.
 * <p>
 * Setters are expected to be called in the following order:
 * <ul>
 * <li>setProfilingStarted</li>
 * <li>setBuildStarted</li>
 * <li>setSettingsEvaluated</li>
 * <li>setProjectsLoaded</li>
 * <li>setProjectsEvaluated</li>
 * <li>setBuildFinished</li>
 * </ul>
 */
public class BuildProfile {

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");

    private final Map<String, ProjectProfile> projects = new LinkedHashMap<String, ProjectProfile>();
    private final Map<String, ContinuousOperation> dependencySets = new LinkedHashMap<String, ContinuousOperation>();
    private long profilingStarted;
    private long buildStarted;
    private long settingsEvaluated;
    private long projectsLoaded;
    private long projectsEvaluated;
    private long buildFinished;
    private StartParameter startParameter;
    private boolean successful;

    public BuildProfile(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    public long getBuildStarted() {
        return buildStarted;
    }

    /**
     * Get a description of this profiled build. It contains info about tasks passed to gradle as targets from the command line.
     */
    public String getBuildDescription() {
        StringBuilder sb = new StringBuilder();
        for (String name : startParameter.getExcludedTaskNames()) {
            sb.append("-x ");
            sb.append(name);
            sb.append(" ");
        }
        for (String name : startParameter.getTaskNames()) {
            sb.append(name);
            sb.append(" ");
        }
        String tasks = sb.toString();
        if (tasks.length() == 0) {
            tasks = "(no tasks specified)";
        }
        return "Profiled build: " + tasks;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    /**
     * Get the profiling container for the specified project
     * @param projectPath to look up
     */
    public ProjectProfile getProjectProfile(String projectPath) {
        ProjectProfile result = projects.get(projectPath);
        if (result == null) {
            result = new ProjectProfile(projectPath);
            projects.put(projectPath, result);
        }
        return result;
    }

    /**
     * Get a list of the profiling containers for all projects
     * @return list
     */
    public List<ProjectProfile> getProjects() {
        return CollectionUtils.sort(projects.values(), Operation.slowestFirst());
    }

    public CompositeOperation<Operation> getProjectConfiguration() {
        List<Operation> operations = new ArrayList<Operation>();
        for (ProjectProfile projectProfile : projects.values()) {
            operations.add(projectProfile.getConfigurationOperation());
        }
        operations = CollectionUtils.sort(operations, Operation.slowestFirst());
        return new CompositeOperation<Operation>(operations);
    }

    public ContinuousOperation getDependencySetProfile(String dependencySetDescription) {
        ContinuousOperation profile = dependencySets.get(dependencySetDescription);
        if (profile == null) {
            profile = new ContinuousOperation(dependencySetDescription);
            dependencySets.put(dependencySetDescription, profile);
        }
        return profile;
    }

    public CompositeOperation<ContinuousOperation> getDependencySets() {
        final List<ContinuousOperation> profiles = CollectionUtils.sort(dependencySets.values(), Operation.slowestFirst());
        return new CompositeOperation<ContinuousOperation>(profiles);
    }

    /**
     * Should be set with a time as soon as possible after startup.
     * @param profilingStarted
     */
    public void setProfilingStarted(long profilingStarted) {
        this.profilingStarted = profilingStarted;
    }

    /**
     * Should be set with a timestamp from a {@link org.gradle.BuildListener#buildStarted}
     * callback.
     * @param buildStarted
     */
    public void setBuildStarted(long buildStarted) {
        this.buildStarted = buildStarted;
    }

    /**
     * Should be set with a timestamp from a {@link org.gradle.BuildListener#settingsEvaluated}
     * callback.
     * @param settingsEvaluated
     */
    public void setSettingsEvaluated(long settingsEvaluated) {
        this.settingsEvaluated = settingsEvaluated;
    }

    /**
     * Should be set with a timestamp from a {@link org.gradle.BuildListener#projectsLoaded}
     * callback.
     * @param projectsLoaded
     */
    public void setProjectsLoaded(long projectsLoaded) {
        this.projectsLoaded = projectsLoaded;
    }

    /**
     * Should be set with a timestamp from a {@link org.gradle.BuildListener#projectsEvaluated}
     * callback.
     * @param projectsEvaluated
     */
    public void setProjectsEvaluated(long projectsEvaluated) {
        this.projectsEvaluated = projectsEvaluated;
    }

    /**
     * Should be set with a timestamp from a {@link org.gradle.BuildListener#buildFinished}
     * callback.
     * @param buildFinished
     */
    public void setBuildFinished(long buildFinished) {
        this.buildFinished = buildFinished;
    }

    /**
     * Get the elapsed time (in mSec) between the start of profiling and the buildStarted event.
     * @return
     */
    public long getElapsedStartup() {
        return buildStarted - profilingStarted;
    }

    /**
     * Get the total elapsed time (in mSec) between the start of profiling and the buildFinished event.
     * @return
     */
    public long getElapsedTotal() {
        return buildFinished - profilingStarted;
    }

    /**
     * Get the elapsed time (in mSec) between the buildStarted event and the settingsEvaluated event.
     * Note that this will include processing of buildSrc as well as the settings file.
     * @return
     */
    public long getElapsedSettings() {
        return settingsEvaluated - buildStarted;
    }

    /**
     * Get the elapsed time (in mSec) between the settingsEvaluated event and the projectsLoaded event.
     * @return
     */
    public long getElapsedProjectsLoading() {
        return projectsLoaded - settingsEvaluated;
    }

    /**
     * Get the elapsed time (in mSec) between the projectsLoaded event and the projectsEvaluated event.
     * @return
     */
    public long getElapsedProjectsConfiguration() {
        return projectsEvaluated - projectsLoaded;
    }

    /**
     * Get the total task execution time from all projects.
     * @return
     */
    public long getElapsedTotalExecutionTime() {
        long result = 0;
        for (ProjectProfile projectProfile : projects.values()) {
            result += projectProfile.getElapsedTime();
        }
        return result;
    }

    public String getBuildStartedDescription() {
        return "Started on: " + DATE_FORMAT.format(buildStarted);
    }

    public StartParameter getStartParameter() {
        return startParameter;
    }


}
