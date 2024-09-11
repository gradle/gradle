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
import org.gradle.api.initialization.Settings;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.internal.CollectionUtils;

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
@ServiceScope(Scope.BuildTree.class)
public class BuildProfile {

    private static final long NOT_INITIALIZED_VALUE = -1L;
    private final Map<String, ProjectProfile> projects = new LinkedHashMap<>();
    private final Map<String, ContinuousOperation> dependencySets = new LinkedHashMap<>();
    private final Map<String, FragmentedOperation> transforms = new LinkedHashMap<>();
    private long profilingStarted = NOT_INITIALIZED_VALUE;
    private long buildStarted = NOT_INITIALIZED_VALUE;
    private long settingsEvaluated = NOT_INITIALIZED_VALUE;
    private long projectsLoaded = NOT_INITIALIZED_VALUE;
    private long buildFinished;
    private final StartParameter startParameter;
    private final BuildStartedTime buildStartedTime;
    private boolean successful;

    public BuildProfile(StartParameter startParameter, BuildStartedTime buildStartedTime) {
        this.startParameter = startParameter;
        this.buildStartedTime = buildStartedTime;
    }

    public long getBuildStarted() {
        return valueOrBuildStartedTimeIfNotInitialized(buildStarted);
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
     *
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
     *
     * @return list
     */
    public List<ProjectProfile> getProjects() {
        return CollectionUtils.sort(projects.values(), Operation.slowestFirst());
    }

    public CompositeOperation<Operation> getProjectConfiguration() {
        List<Operation> operations = new ArrayList<>();
        for (ProjectProfile projectProfile : projects.values()) {
            operations.add(projectProfile.getConfigurationOperation());
        }
        operations = CollectionUtils.sort(operations, Operation.slowestFirst());
        return new CompositeOperation<>(operations);
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
        return new CompositeOperation<>(profiles);
    }

    public FragmentedOperation getTransformProfile(String transformDescription) {
        FragmentedOperation profile = transforms.get(transformDescription);
        if (profile == null) {
            profile = new FragmentedOperation(transformDescription);
            transforms.put(transformDescription, profile);
        }
        return profile;
    }

    public CompositeOperation<FragmentedOperation> getTransforms() {
        final List<FragmentedOperation> profiles = CollectionUtils.sort(transforms.values(), Operation.slowestFirst());
        return new CompositeOperation<>(profiles);
    }

    /**
     * Should be set with a time as soon as possible after startup.
     */
    public void setProfilingStarted(long profilingStarted) {
        this.profilingStarted = profilingStarted;
    }

    /**
     * Should be set with a timestamp from a {@link org.gradle.BuildListener#beforeSettings(Settings)}
     * callback.
     */
    public void setBuildStarted(long buildStarted) {
        this.buildStarted = buildStarted;
    }

    /**
     * Should be set with a timestamp from a {@link org.gradle.BuildListener#settingsEvaluated}
     * callback.
     */
    public void setSettingsEvaluated(long settingsEvaluated) {
        this.settingsEvaluated = settingsEvaluated;
    }

    /**
     * Should be set with a timestamp from a {@link org.gradle.BuildListener#projectsLoaded}
     * callback.
     */
    public void setProjectsLoaded(long projectsLoaded) {
        this.projectsLoaded = projectsLoaded;
    }

    /**
     * Should be set with a timestamp from a {@link org.gradle.BuildListener#buildFinished}
     * callback.
     */
    public void setBuildFinished(long buildFinished) {
        this.buildFinished = buildFinished;
    }

    /**
     * Get the elapsed time (in mSec) between the start of profiling and the buildStarted event.
     */
    public long getElapsedStartup() {
        return valueOrBuildStartedTimeIfNotInitialized(buildStarted) - valueOrBuildStartedTimeIfNotInitialized(profilingStarted);
    }

    /**
     * Get the total elapsed time (in mSec) between the start of profiling and the buildFinished event.
     */
    public long getElapsedTotal() {
        return buildFinished - valueOrBuildStartedTimeIfNotInitialized(profilingStarted);
    }

    /**
     * Get the elapsed time (in mSec) between the buildStarted event and the settingsEvaluated event.
     * Note that this will include processing of buildSrc as well as the settings file.
     */
    public long getElapsedSettings() {
        return valueOrBuildStartedTimeIfNotInitialized(settingsEvaluated) - valueOrBuildStartedTimeIfNotInitialized(buildStarted);
    }

    /**
     * Get the elapsed time (in mSec) between the settingsEvaluated event and the projectsLoaded event.
     */
    public long getElapsedProjectsLoading() {
        return valueOrBuildStartedTimeIfNotInitialized(projectsLoaded) - valueOrBuildStartedTimeIfNotInitialized(settingsEvaluated);
    }

    /**
     * Get the total artifact transform time.
     */
    public long getElapsedArtifactTransformTime() {
        long result = 0;
        for (FragmentedOperation transform : transforms.values()) {
            result += transform.getElapsedTime();
        }
        return result;
    }

    /**
     * Get the total task execution time from all projects.
     */
    public long getElapsedTotalExecutionTime() {
        long result = 0;
        for (ProjectProfile projectProfile : projects.values()) {
            result += projectProfile.getElapsedTime();
        }

        return result;
    }

    public String getBuildStartedDescription() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");
        return "Started on: " + dateFormat.format(valueOrBuildStartedTimeIfNotInitialized(buildStarted));
    }

    /*
     * When loading from configuration cache, the fields that are set on configuration time ain't initialized.
     * After configuration cache hit it's fair to use build start time value for them.
     * */
    private long valueOrBuildStartedTimeIfNotInitialized(long time) {
        return time == NOT_INITIALIZED_VALUE ? buildStartedTime.getStartTime() : time;
    }
}
