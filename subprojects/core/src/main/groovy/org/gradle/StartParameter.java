/*
 * Copyright 2010 the original author or authors.
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

package org.gradle;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.gradle.api.Incubating;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.CompositeInitScriptFinder;
import org.gradle.initialization.DistributionInitScriptFinder;
import org.gradle.initialization.UserHomeInitScriptFinder;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.logging.LoggingConfiguration;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * <p>{@code StartParameter} defines the configuration used by a Gradle instance to execute a build. The properties of {@code StartParameter} generally
 * correspond to the command-line options of Gradle.
 *
 * <p>You can obtain an instance of a {@code StartParameter} by either creating a new one, or duplicating an existing one using {@link #newInstance} or {@link #newBuild}.</p>
 *
 * @see org.gradle.initialization.GradleLauncher
 */
public class StartParameter extends LoggingConfiguration implements Serializable {
    public static final String GRADLE_USER_HOME_PROPERTY_KEY = BuildLayoutParameters.GRADLE_USER_HOME_PROPERTY_KEY;

    /**
     * The default user home directory.
     */
    public static final File DEFAULT_GRADLE_USER_HOME = new BuildLayoutParameters().getGradleUserHomeDir();

    private List<TaskExecutionRequest> taskRequests = new ArrayList<TaskExecutionRequest>();
    private Set<String> excludedTaskNames = new LinkedHashSet<String>();
    private boolean buildProjectDependencies = true;
    private File currentDir;
    private File projectDir;
    private boolean searchUpwards;
    private Map<String, String> projectProperties = new HashMap<String, String>();
    private Map<String, String> systemPropertiesArgs = new HashMap<String, String>();
    private File gradleUserHomeDir;
    private File gradleHomeDir;
    private File settingsFile;
    private boolean useEmptySettings;
    private File buildFile;
    private List<File> initScripts = new ArrayList<File>();
    private boolean dryRun;
    private boolean rerunTasks;
    private boolean profile;
    private boolean continueOnFailure;
    private boolean offline;
    private File projectCacheDir;
    private boolean refreshDependencies;
    private boolean recompileScripts;
    private int parallelThreadCount;
    private boolean configureOnDemand;

    /**
     * Sets the project's cache location. Set to null to use the default location.
     */
    public void setProjectCacheDir(File projectCacheDir) {
        this.projectCacheDir = projectCacheDir;
    }

    /**
     * Returns the project's cache dir.
     *
     * @return project's cache dir, or null if the default location is to be used.
     */
    public File getProjectCacheDir() {
        return projectCacheDir;
    }

    /**
     * Creates a {@code StartParameter} with default values. This is roughly equivalent to running Gradle on the command-line with no arguments.
     */
    public StartParameter() {
        gradleHomeDir = new DefaultModuleRegistry().getGradleHome();

        BuildLayoutParameters layoutParameters = new BuildLayoutParameters();
        searchUpwards = layoutParameters.getSearchUpwards();
        currentDir = layoutParameters.getCurrentDir();
        projectDir = layoutParameters.getProjectDir();
        gradleUserHomeDir = layoutParameters.getGradleUserHomeDir();
    }

    /**
     * Duplicates this {@code StartParameter} instance.
     *
     * @return the new parameters.
     */
    public StartParameter newInstance() {
        return prepareNewInstance(new StartParameter());
    }

    protected StartParameter prepareNewInstance(StartParameter p) {
        prepareNewBuild(p);
        p.buildFile = buildFile;
        p.projectDir = projectDir;
        p.settingsFile = settingsFile;
        p.useEmptySettings = useEmptySettings;
        p.taskRequests = new ArrayList<TaskExecutionRequest>(taskRequests);
        p.excludedTaskNames = new LinkedHashSet<String>(excludedTaskNames);
        p.buildProjectDependencies = buildProjectDependencies;
        p.currentDir = currentDir;
        p.searchUpwards = searchUpwards;
        p.projectProperties = new HashMap<String, String>(projectProperties);
        p.systemPropertiesArgs = new HashMap<String, String>(systemPropertiesArgs);
        p.gradleHomeDir = gradleHomeDir;
        p.initScripts = new ArrayList<File>(initScripts);
        p.dryRun = dryRun;
        p.projectCacheDir = projectCacheDir;
        return p;
    }

    /**
     * <p>Creates the parameters for a new build, using these parameters as a template. Copies the environmental properties from this parameter (eg Gradle user home dir, etc), but does not copy the
     * build specific properties (eg task names).</p>
     *
     * @return The new parameters.
     */
    public StartParameter newBuild() {
        return prepareNewBuild(new StartParameter());
    }

    protected StartParameter prepareNewBuild(StartParameter p) {
        p.gradleUserHomeDir = gradleUserHomeDir;
        p.setLogLevel(getLogLevel());
        p.setColorOutput(isColorOutput());
        p.setShowStacktrace(getShowStacktrace());
        p.profile = profile;
        p.continueOnFailure = continueOnFailure;
        p.offline = offline;
        p.rerunTasks = rerunTasks;
        p.recompileScripts = recompileScripts;
        p.refreshDependencies = refreshDependencies;
        p.parallelThreadCount = parallelThreadCount;
        p.configureOnDemand = configureOnDemand;
        return p;
    }

    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * Returns the build file to use to select the default project. Returns null when the build file is not used to select the default project.
     *
     * @return The build file. May be null.
     */
    public File getBuildFile() {
        return buildFile;
    }

    /**
     * Sets the build file to use to select the default project. Use null to disable selecting the default project using the build file.
     *
     * @param buildFile The build file. May be null.
     */
    public void setBuildFile(File buildFile) {
        if (buildFile == null) {
            this.buildFile = null;
            setCurrentDir(null);
        } else {
            this.buildFile = GFileUtils.canonicalise(buildFile);
            setProjectDir(this.buildFile.getParentFile());
        }
    }

    /**
     * Specifies that an empty settings script should be used.
     *
     * This means that even if a settings file exists in the conventional location, or has been previously specified by {@link #setSettingsFile(java.io.File)}, it will not be used.
     *
     * If {@link #setSettingsFile(java.io.File)} is called after this, it will supersede calling this method.
     *
     * @return this
     */
    public StartParameter useEmptySettings() {
        searchUpwards = false;
        useEmptySettings = true;
        settingsFile = null;
        return this;
    }

    /**
     * Returns whether an empty settings script will be used regardless of whether one exists in the default location.
     *
     * @return Whether to use empty settings or not.
     */
    public boolean isUseEmptySettings() {
        return useEmptySettings;
    }

    /**
     * Returns the names of the tasks to execute in this build. When empty, the default tasks for the project will be executed.
     * If {@link TaskExecutionRequest}s are set for this build then names from these task parameters are returned.
     *
     * @return the names of the tasks to execute in this build. Never returns null.
     */
    public List<String> getTaskNames() {
        List<String> taskNames = Lists.newArrayList();
        for (TaskExecutionRequest taskRequest : taskRequests) {
            taskNames.addAll(taskRequest.getArgs());
        }
        return taskNames;
    }

    /**
     * <p>Sets the tasks to execute in this build. Set to an empty list, or null, to execute the default tasks for the project. The tasks are executed in the order provided, subject to dependency
     * between the tasks.</p>
     *
     * @param taskNames the names of the tasks to execute in this build.
     */
    public void setTaskNames(Iterable<String> taskNames) {
        if (taskNames == null) {
            this.taskRequests = Collections.emptyList();
        } else {
            this.taskRequests = Arrays.<TaskExecutionRequest>asList(new DefaultTaskExecutionRequest(taskNames));
        }
    }

    /**
     * Returns the tasks to execute in this build. When empty, the default tasks for the project will be executed.
     *
     * @return the tasks to execute in this build. Never returns null.
     */
    @Incubating
    public List<TaskExecutionRequest> getTaskRequests() {
        return taskRequests;
    }

    /**
     * <p>Sets the task parameters to execute in this build. Set to an empty list, to execute the default tasks for the project. The tasks are executed in the order provided, subject to dependency
     * between the tasks.</p>
     *
     * @param taskParameters the tasks to execute in this build.
     */
    @Incubating
    public void setTaskRequests(Iterable<? extends TaskExecutionRequest> taskParameters) {
        this.taskRequests = Lists.newArrayList(taskParameters);
    }

    /**
     * Returns the names of the tasks to be excluded from this build. When empty, no tasks are excluded from the build.
     *
     * @return The names of the excluded tasks. Returns an empty set if there are no such tasks.
     */
    public Set<String> getExcludedTaskNames() {
        return excludedTaskNames;
    }

    /**
     * Sets the tasks to exclude from this build.
     *
     * @param excludedTaskNames The task names. Can be null.
     */
    public void setExcludedTaskNames(Iterable<String> excludedTaskNames) {
        this.excludedTaskNames = Sets.newLinkedHashSet(excludedTaskNames);
    }

    /**
     * Returns the directory to use to select the default project, and to search for the settings file.
     *
     * @return The current directory. Never returns null.
     */
    public File getCurrentDir() {
        return currentDir;
    }

    /**
     * Sets the directory to use to select the default project, and to search for the settings file. Set to null to use the default current directory.
     *
     * @param currentDir The directory. Set to null to use the default.
     */
    public void setCurrentDir(File currentDir) {
        if (currentDir != null) {
            this.currentDir = GFileUtils.canonicalise(currentDir);
        } else {
            this.currentDir = new BuildLayoutParameters().getCurrentDir();
        }
    }

    public boolean isSearchUpwards() {
        return searchUpwards;
    }

    public void setSearchUpwards(boolean searchUpwards) {
        this.searchUpwards = searchUpwards;
    }

    public Map<String, String> getProjectProperties() {
        return projectProperties;
    }

    public void setProjectProperties(Map<String, String> projectProperties) {
        this.projectProperties = projectProperties;
    }

    public Map<String, String> getSystemPropertiesArgs() {
        return systemPropertiesArgs;
    }

    public void setSystemPropertiesArgs(Map<String, String> systemPropertiesArgs) {
        this.systemPropertiesArgs = systemPropertiesArgs;
    }

    /**
     * Returns the directory to use as the user home directory.
     *
     * @return The home directory.
     */
    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    /**
     * Sets the directory to use as the user home directory. Set to null to use the default directory.
     *
     * @param gradleUserHomeDir The home directory. May be null.
     */
    public void setGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir == null ? new BuildLayoutParameters().getGradleUserHomeDir() : GFileUtils.canonicalise(gradleUserHomeDir);
    }

    /**
     * Returns true if project dependencies are to be built, false if they should not be. The default is true.
     */
    public boolean isBuildProjectDependencies() {
        return buildProjectDependencies;
    }

    /**
     * Specifies whether project dependencies should be built. Defaults to true.
     *
     * @return this
     */
    public StartParameter setBuildProjectDependencies(boolean build) {
        this.buildProjectDependencies = build;
        return this;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Sets the settings file to use for the build. Use null to use the default settings file.
     *
     * @param settingsFile The settings file to use. May be null.
     */
    public void setSettingsFile(File settingsFile) {
        if (settingsFile == null) {
            this.settingsFile = null;
        } else {
            this.useEmptySettings = false;
            this.settingsFile = GFileUtils.canonicalise(settingsFile);
            currentDir = this.settingsFile.getParentFile();
        }
    }

    /**
     * Returns the explicit settings file to use for the build, or null.
     *
     * Will return null if the default settings file is to be used. However, if {@link #isUseEmptySettings()} returns true, then no settings file at all will be used.
     *
     * @return The settings file. May be null.
     * @see #isUseEmptySettings()
     */
    public File getSettingsFile() {
        return settingsFile;
    }

    /**
     * Adds the given file to the list of init scripts that are run before the build starts.  This list is in addition to the default init scripts.
     *
     * @param initScriptFile The init scripts.
     */
    public void addInitScript(File initScriptFile) {
        initScripts.add(initScriptFile);
    }

    /**
     * Sets the list of init scripts to be run before the build starts. This list is in addition to the default init scripts.
     *
     * @param initScripts The init scripts.
     */
    public void setInitScripts(List<File> initScripts) {
        this.initScripts = initScripts;
    }

    /**
     * Returns all explicitly added init scripts that will be run before the build starts.  This list does not contain the user init script located in ${user.home}/.gradle/init.gradle, even though
     * that init script will also be run.
     *
     * @return list of all explicitly added init scripts.
     */
    public List<File> getInitScripts() {
        return Collections.unmodifiableList(initScripts);
    }

    /**
     * Returns all init scripts, including explicit init scripts and implicit init scripts.
     *
     * @return All init scripts, including explicit init scripts and implicit init scripts.
     */
    @Incubating
    public List<File> getAllInitScripts() {
        CompositeInitScriptFinder initScriptFinder = new CompositeInitScriptFinder(
                new UserHomeInitScriptFinder(getGradleUserHomeDir()), new DistributionInitScriptFinder(gradleHomeDir)
        );

        List<File> scripts = new ArrayList<File>(getInitScripts());
        initScriptFinder.findScripts(scripts);
        return Collections.unmodifiableList(scripts);
    }

    /**
     * Sets the project directory to use to select the default project. Use null to use the default criteria for selecting the default project.
     *
     * @param projectDir The project directory. May be null.
     */
    public void setProjectDir(File projectDir) {
        if (projectDir == null) {
            setCurrentDir(null);
            this.projectDir = null;
        } else {
            File canonicalFile = GFileUtils.canonicalise(projectDir);
            currentDir = canonicalFile;
            this.projectDir = canonicalFile;
        }
    }

    /**
     * Returns the project dir to use to select the default project.
     *
     * Returns null when the build file is not used to select the default project
     *
     * @return The project dir. May be null.
     */
    public File getProjectDir() {
        return projectDir;
    }

    /**
     * Specifies if a profile report should be generated.
     *
     * @param profile true if a profile report should be generated
     */
    public void setProfile(boolean profile) {
        this.profile = profile;
    }

    /**
     * Returns true if a profile report will be generated.
     */
    public boolean isProfile() {
        return profile;
    }

    /**
     * Specifies whether the build should continue on task failure. The default is false.
     */
    public boolean isContinueOnFailure() {
        return continueOnFailure;
    }

    /**
     * Specifies whether the build should continue on task failure. The default is false.
     */
    public void setContinueOnFailure(boolean continueOnFailure) {
        this.continueOnFailure = continueOnFailure;
    }

    /**
     * Specifies whether the build should be performed offline (ie without network access).
     */
    public boolean isOffline() {
        return offline;
    }

    /**
     * Specifies whether the build should be performed offline (ie without network access).
     */
    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    /**
     * Specifies whether the dependencies should be refreshed..
     */
    public boolean isRefreshDependencies() {
        return refreshDependencies;
    }

    /**
     * Specifies whether the dependencies should be refreshed..
     */
    public void setRefreshDependencies(boolean refreshDependencies) {
        this.refreshDependencies = refreshDependencies;
    }

    /**
     * Specifies whether the cached task results should be ignored and each task should be forced to be executed.
     */
    public boolean isRerunTasks() {
        return rerunTasks;
    }

    /**
     * Specifies whether the cached task results should be ignored and each task should be forced to be executed.
     */
    public void setRerunTasks(boolean rerunTasks) {
        this.rerunTasks = rerunTasks;
    }

    /**
     * Specifies whether the build scripts should be recompiled.
     */
    public boolean isRecompileScripts() {
        return recompileScripts;
    }

    /**
     * Specifies whether the build scripts should be recompiled.
     */
    public void setRecompileScripts(boolean recompileScripts) {
        this.recompileScripts = recompileScripts;
    }

    /**
     * Returns the number of parallel threads to use for build execution.
     *
     * <0: Automatically determine the optimal number of executors to use.
     *  0: Do not use parallel execution.
     * >0: Use this many parallel execution threads.
     */
    public int getParallelThreadCount() {
        return parallelThreadCount;
    }

    /**
     * Specifies the number of parallel threads to use for build execution.
     * 
     * @see #getParallelThreadCount()
     */
    public void setParallelThreadCount(int parallelThreadCount) {
        this.parallelThreadCount = parallelThreadCount;
    }

    /**
     * If the configure-on-demand mode is active
     */
    @Incubating
    public boolean isConfigureOnDemand() {
        return configureOnDemand;
    }

    @Override
    public String toString() {
        return "StartParameter{"
                + "taskRequests=" + taskRequests
                + ", excludedTaskNames=" + excludedTaskNames
                + ", currentDir=" + currentDir
                + ", searchUpwards=" + searchUpwards
                + ", projectProperties=" + projectProperties
                + ", systemPropertiesArgs=" + systemPropertiesArgs
                + ", gradleUserHomeDir=" + gradleUserHomeDir
                + ", gradleHome=" + gradleHomeDir
                + ", logLevel=" + getLogLevel()
                + ", showStacktrace=" + getShowStacktrace()
                + ", buildFile=" + buildFile
                + ", initScripts=" + initScripts
                + ", dryRun=" + dryRun
                + ", rerunTasks=" + rerunTasks
                + ", recompileScripts=" + recompileScripts
                + ", offline=" + offline
                + ", refreshDependencies=" + refreshDependencies
                + ", parallelThreadCount=" + parallelThreadCount
                + ", configureOnDemand=" + configureOnDemand
                + '}';
    }

    /**
     * Package scope for testing purposes.
     */
    void setGradleHomeDir(File gradleHomeDir) {
        this.gradleHomeDir = gradleHomeDir;
    }

    @Incubating
    public void setConfigureOnDemand(boolean configureOnDemand) {
        this.configureOnDemand = configureOnDemand;
    }
}