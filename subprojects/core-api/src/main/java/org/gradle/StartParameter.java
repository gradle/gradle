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
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration;
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.CompositeInitScriptFinder;
import org.gradle.initialization.DistributionInitScriptFinder;
import org.gradle.initialization.UserHomeInitScriptFinder;
import org.gradle.internal.DefaultTaskExecutionRequest;
import org.gradle.internal.FileUtils;
import org.gradle.internal.RunDefaultTasksExecutionRequest;
import org.gradle.internal.concurrent.DefaultParallelismConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.logging.DefaultLoggingConfiguration;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyList;

/**
 * <p>{@code StartParameter} defines the configuration used by a Gradle instance to execute a build. The properties of {@code StartParameter} generally correspond to the command-line options of
 * Gradle.
 *
 * <p>You can obtain an instance of a {@code StartParameter} by either creating a new one, or duplicating an existing one using {@link #newInstance} or {@link #newBuild}.</p>
 */
public class StartParameter implements LoggingConfiguration, ParallelismConfiguration, Serializable {
    public static final String GRADLE_USER_HOME_PROPERTY_KEY = BuildLayoutParameters.GRADLE_USER_HOME_PROPERTY_KEY;

    /**
     * The default user home directory.
     */
    public static final File DEFAULT_GRADLE_USER_HOME = new BuildLayoutParameters().getGradleUserHomeDir();

    private final DefaultLoggingConfiguration loggingConfiguration = new DefaultLoggingConfiguration();
    private final DefaultParallelismConfiguration parallelismConfiguration = new DefaultParallelismConfiguration();
    private List<TaskExecutionRequest> taskRequests = new ArrayList<>();
    private Set<String> excludedTaskNames = new LinkedHashSet<>();
    private boolean buildProjectDependencies = true;
    private File currentDir;
    private File projectDir;
    private Map<String, String> projectProperties = new HashMap<>();
    private Map<String, String> systemPropertiesArgs = new HashMap<>();
    private File gradleUserHomeDir;
    protected File gradleHomeDir;
    private File settingsFile;
    private File buildFile;
    private List<File> initScripts = new ArrayList<>();
    private boolean dryRun;
    private boolean rerunTasks;
    private boolean profile;
    private boolean continueOnFailure;
    private boolean offline;
    private File projectCacheDir;
    private boolean refreshDependencies;
    private boolean buildCacheEnabled;
    private boolean buildCacheDebugLogging;
    private boolean configureOnDemand;
    private boolean continuous;
    private List<File> includedBuilds = new ArrayList<>();
    private boolean buildScan;
    private boolean noBuildScan;
    private boolean writeDependencyLocks;
    private List<String> writeDependencyVerifications = emptyList();
    private List<String> lockedDependenciesToUpdate = emptyList();
    private DependencyVerificationMode verificationMode = DependencyVerificationMode.STRICT;
    private boolean isRefreshKeys;
    private boolean isExportKeys;
    private WelcomeMessageConfiguration welcomeMessageConfiguration = new WelcomeMessageConfiguration(WelcomeMessageDisplayMode.ONCE);

    /**
     * {@inheritDoc}
     */
    @Override
    public LogLevel getLogLevel() {
        return loggingConfiguration.getLogLevel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogLevel(LogLevel logLevel) {
        loggingConfiguration.setLogLevel(logLevel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ShowStacktrace getShowStacktrace() {
        return loggingConfiguration.getShowStacktrace();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setShowStacktrace(ShowStacktrace showStacktrace) {
        loggingConfiguration.setShowStacktrace(showStacktrace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsoleOutput getConsoleOutput() {
        return loggingConfiguration.getConsoleOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConsoleOutput(ConsoleOutput consoleOutput) {
        loggingConfiguration.setConsoleOutput(consoleOutput);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WarningMode getWarningMode() {
        return loggingConfiguration.getWarningMode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWarningMode(WarningMode warningMode) {
        loggingConfiguration.setWarningMode(warningMode);
    }

    /**
     * Sets the project's cache location. Set to null to use the default location.
     */
    public void setProjectCacheDir(@Nullable File projectCacheDir) {
        this.projectCacheDir = projectCacheDir;
    }

    /**
     * Returns the project's cache dir.
     *
     * <p>Note that this directory is managed by Gradle, and it assumes full ownership of its contents.
     * Plugins and build logic should not store or modify any files or directories within this cache directory.
     *
     * @return project's cache dir, or null if the default location is to be used.
     */
    @Nullable
    public File getProjectCacheDir() {
        return projectCacheDir;
    }

    /**
     * Creates a {@code StartParameter} with default values. This is roughly equivalent to running Gradle on the command-line with no arguments.
     */
    public StartParameter() {
        this(new BuildLayoutParameters());
    }

    /**
     * Creates a {@code StartParameter} initialized from the given {@link BuildLayoutParameters}.
     * @since 7.0
     */
    protected StartParameter(BuildLayoutParameters layoutParameters) {
        gradleHomeDir = layoutParameters.getGradleInstallationHomeDir();
        currentDir = layoutParameters.getCurrentDir();
        projectDir = layoutParameters.getProjectDir();
        gradleUserHomeDir = layoutParameters.getGradleUserHomeDir();
        setTaskNames(null);
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
        p.setWarningMode(getWarningMode());
        p.buildFile = buildFile;
        p.projectDir = projectDir;
        p.settingsFile = settingsFile;
        p.taskRequests = new ArrayList<>(taskRequests);
        p.excludedTaskNames = new LinkedHashSet<>(excludedTaskNames);
        p.buildProjectDependencies = buildProjectDependencies;
        p.currentDir = currentDir;
        p.projectProperties = new HashMap<>(projectProperties);
        p.systemPropertiesArgs = new HashMap<>(systemPropertiesArgs);
        p.initScripts = new ArrayList<>(initScripts);
        p.includedBuilds = new ArrayList<>(includedBuilds);
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
        p.gradleHomeDir = gradleHomeDir;
        p.setLogLevel(getLogLevel());
        p.setConsoleOutput(getConsoleOutput());
        p.setShowStacktrace(getShowStacktrace());
        p.setWarningMode(getWarningMode());
        p.profile = profile;
        p.continueOnFailure = continueOnFailure;
        p.offline = offline;
        p.rerunTasks = rerunTasks;
        p.refreshDependencies = refreshDependencies;
        p.setParallelProjectExecutionEnabled(isParallelProjectExecutionEnabled());
        p.buildCacheEnabled = buildCacheEnabled;
        p.configureOnDemand = configureOnDemand;
        p.setMaxWorkerCount(getMaxWorkerCount());
        p.systemPropertiesArgs = new HashMap<>(systemPropertiesArgs);
        p.writeDependencyLocks = writeDependencyLocks;
        p.writeDependencyVerifications = writeDependencyVerifications;
        p.lockedDependenciesToUpdate = new ArrayList<>(lockedDependenciesToUpdate);
        p.verificationMode = verificationMode;
        p.isRefreshKeys = isRefreshKeys;
        p.isExportKeys = isExportKeys;
        p.welcomeMessageConfiguration = welcomeMessageConfiguration;
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
     *
     * @deprecated Setting custom build file to select the default project has been deprecated.
     * This method will be removed in Gradle 9.0.
     */
    @Deprecated
    @Nullable
    public File getBuildFile() {
        logBuildOrSettingsFileDeprecation("buildFile");
        return buildFile;
    }

    /**
     * Sets the build file to use to select the default project. Use null to disable selecting the default project using the build file.
     *
     * @param buildFile The build file. May be null.
     *
     * @deprecated Setting custom build file to select the default project has been deprecated.
     * Please use {@link #setProjectDir(File)} to specify the directory of the default project instead.
     * This method will be removed in Gradle 9.0.
     */
    @Deprecated
    public void setBuildFile(@Nullable File buildFile) {
        logBuildOrSettingsFileDeprecation("buildFile");
        if (buildFile == null) {
            this.buildFile = null;
            setCurrentDir(null);
        } else {
            this.buildFile = FileUtils.canonicalize(buildFile);
            setProjectDir(this.buildFile.getParentFile());
        }
    }

    /**
     * Returns the names of the tasks to execute in this build. When empty, the default tasks for the project will be executed. If {@link TaskExecutionRequest}s are set for this build then names from these task parameters are returned.
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
    public void setTaskNames(@Nullable Iterable<String> taskNames) {
        if (taskNames == null) {
            this.taskRequests = Collections.singletonList(new RunDefaultTasksExecutionRequest());
        } else {
            this.taskRequests = Collections.singletonList(DefaultTaskExecutionRequest.of(taskNames));
        }
    }

    /**
     * Returns the tasks to execute in this build. When empty, the default tasks for the project will be executed.
     *
     * @return the tasks to execute in this build. Never returns null.
     */
    public List<TaskExecutionRequest> getTaskRequests() {
        return taskRequests;
    }

    /**
     * <p>Sets the task parameters to execute in this build. Set to an empty list, to execute the default tasks for the project. The tasks are executed in the order provided, subject to dependency
     * between the tasks.</p>
     *
     * @param taskParameters the tasks to execute in this build.
     */
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
     * @param excludedTaskNames The task names.
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
    public void setCurrentDir(@Nullable File currentDir) {
        if (currentDir != null) {
            this.currentDir = FileUtils.canonicalize(currentDir);
        } else {
            this.currentDir = new BuildLayoutParameters().getCurrentDir();
        }
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
    public void setGradleUserHomeDir(@Nullable File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir == null ? new BuildLayoutParameters().getGradleUserHomeDir() : FileUtils.canonicalize(gradleUserHomeDir);
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
     *
     * @deprecated Setting custom settings file for the build has been deprecated.
     * Please use {@link #setProjectDir(File)} to specify the directory of the default project instead.
     * This method will be removed in Gradle 9.0.
     */
    @Deprecated
    public void setSettingsFile(@Nullable File settingsFile) {
        logBuildOrSettingsFileDeprecation("settingsFile");
        if (settingsFile == null) {
            this.settingsFile = null;
        } else {
            this.settingsFile = FileUtils.canonicalize(settingsFile);
            currentDir = this.settingsFile.getParentFile();
        }
    }

    /**
     * Returns the explicit settings file to use for the build, or null.
     *
     * Will return null if the default settings file is to be used.
     *
     * @return The settings file. May be null.
     *
     * @deprecated Setting custom build file to select the default project has been deprecated.
     * This method will be removed in Gradle 9.0.
     */
    @Deprecated
    @Nullable
    public File getSettingsFile() {
        logBuildOrSettingsFileDeprecation("settingsFile");
        return settingsFile;
    }

    private void logBuildOrSettingsFileDeprecation(String propertyName) {
        DeprecationLogger.deprecateProperty(StartParameter.class, propertyName)
            .withContext("Setting custom build file to select the default project has been deprecated.")
            .withAdvice("Please use 'projectDir' to specify the directory of the default project instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "configuring_custom_build_layout")
            .nagUser();
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
    public List<File> getAllInitScripts() {
        CompositeInitScriptFinder initScriptFinder = new CompositeInitScriptFinder(
            new UserHomeInitScriptFinder(getGradleUserHomeDir()),
            new DistributionInitScriptFinder(gradleHomeDir)
        );

        List<File> scripts = new ArrayList<>(getInitScripts());
        initScriptFinder.findScripts(scripts);
        return Collections.unmodifiableList(scripts);
    }

    /**
     * Sets the project directory to use to select the default project. Use null to use the default criteria for selecting the default project.
     *
     * @param projectDir The project directory. May be null.
     */
    public void setProjectDir(@Nullable File projectDir) {
        if (projectDir == null) {
            setCurrentDir(null);
            this.projectDir = null;
        } else {
            File canonicalFile = FileUtils.canonicalize(projectDir);
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
    @Nullable
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
     * {@inheritDoc}
     */
    @Override
    public boolean isParallelProjectExecutionEnabled() {
        return parallelismConfiguration.isParallelProjectExecutionEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setParallelProjectExecutionEnabled(boolean parallelProjectExecution) {
        parallelismConfiguration.setParallelProjectExecutionEnabled(parallelProjectExecution);
    }

    /**
     * Returns true if the build cache is enabled.
     *
     * @since 3.5
     */
    public boolean isBuildCacheEnabled() {
        return buildCacheEnabled;
    }

    /**
     * Enables/disables the build cache.
     *
     * @since 3.5
     */
    public void setBuildCacheEnabled(boolean buildCacheEnabled) {
        this.buildCacheEnabled = buildCacheEnabled;
    }

    /**
     * Whether build cache debug logging is enabled.
     *
     * @since 4.6
     */
    public boolean isBuildCacheDebugLogging() {
        return buildCacheDebugLogging;
    }

    /**
     * Whether build cache debug logging is enabled.
     *
     * @since 4.6
     */
    public void setBuildCacheDebugLogging(boolean buildCacheDebugLogging) {
        this.buildCacheDebugLogging = buildCacheDebugLogging;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaxWorkerCount() {
        return parallelismConfiguration.getMaxWorkerCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxWorkerCount(int maxWorkerCount) {
        parallelismConfiguration.setMaxWorkerCount(maxWorkerCount);
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
            + ", projectDir=" + projectDir
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
            + ", offline=" + offline
            + ", refreshDependencies=" + refreshDependencies
            + ", parallelProjectExecution=" + isParallelProjectExecutionEnabled()
            + ", configureOnDemand=" + configureOnDemand
            + ", maxWorkerCount=" + getMaxWorkerCount()
            + ", buildCacheEnabled=" + buildCacheEnabled
            + ", writeDependencyLocks=" + writeDependencyLocks
            + ", verificationMode=" + verificationMode
            + ", refreshKeys=" + isRefreshKeys
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

    public boolean isContinuous() {
        return continuous;
    }

    public void setContinuous(boolean enabled) {
        this.continuous = enabled;
    }

    public void includeBuild(File includedBuild) {
        includedBuilds.add(includedBuild);
    }

    public void setIncludedBuilds(List<File> includedBuilds) {
        this.includedBuilds = includedBuilds;
    }

    public List<File> getIncludedBuilds() {
        return Collections.unmodifiableList(includedBuilds);
    }

    /**
     * Returns true if build scan should be created.
     *
     * @since 3.4
     */
    public boolean isBuildScan() {
        return buildScan;
    }

    /**
     * Specifies whether a build scan should be created.
     *
     * @since 3.4
     */
    public void setBuildScan(boolean buildScan) {
        this.buildScan = buildScan;
    }

    /**
     * Returns true when build scan creation is explicitly disabled.
     *
     * @since 3.4
     */
    public boolean isNoBuildScan() {
        return noBuildScan;
    }

    /**
     * Specifies whether build scan creation is explicitly disabled.
     *
     * @since 3.4
     */
    public void setNoBuildScan(boolean noBuildScan) {
        this.noBuildScan = noBuildScan;
    }

    /**
     * Specifies whether dependency resolution needs to be persisted for locking
     *
     * @since 4.8
     */
    public void setWriteDependencyLocks(boolean writeDependencyLocks) {
        this.writeDependencyLocks = writeDependencyLocks;
    }

    /**
     * Returns true when dependency resolution is to be persisted for locking
     *
     * @since 4.8
     */
    public boolean isWriteDependencyLocks() {
        return writeDependencyLocks;
    }

    /**
     * Indicates that specified dependencies are to be allowed to update their version.
     * Implicitly activates dependency locking persistence.
     *
     * @param lockedDependenciesToUpdate the modules to update
     * @see #isWriteDependencyLocks()
     * @since 4.8
     */
    public void setLockedDependenciesToUpdate(List<String> lockedDependenciesToUpdate) {
        this.lockedDependenciesToUpdate = Lists.newArrayList(lockedDependenciesToUpdate);
        this.writeDependencyLocks = true;
    }

    /**
     * Indicates if a dependency verification metadata file should be written at the
     * end of this build. If the list is not empty, then it means we need to generate
     * or update the dependency verification file with the checksums specified in the
     * list.
     *
     * @since 6.1
     */
    public List<String> getWriteDependencyVerifications() {
        return writeDependencyVerifications;
    }

    /**
     * Tells if a dependency verification metadata file should be written at the end
     * of this build.
     *
     * @param checksums the list of checksums to generate
     * @since 6.1
     */
    public void setWriteDependencyVerifications(List<String> checksums) {
        this.writeDependencyVerifications = checksums;
    }

    /**
     * Returns the list of modules that are to be allowed to update their version compared to the lockfile.
     *
     * @return a list of modules allowed to have a version update
     * @since 4.8
     */
    public List<String> getLockedDependenciesToUpdate() {
        return lockedDependenciesToUpdate;
    }

    /**
     * Sets the dependency verification mode. There are three different modes:
     * <ul>
     *     <li><i>strict</i>, the default, verification is enabled as soon as a dependency verification file is present.</li>
     *     <li><i>lenient</i>, in this mode, failure to verify a checksum, missing checksums or signatures will be logged
     *     but will not fail the build. This mode should only be used when updating dependencies as it is inherently unsafe.</li>
     *     <li><i>off</i>, this mode disables all verifications</li>
     * </ul>
     *
     * @param verificationMode if true, enables lenient dependency verification
     * @since 6.2
     */
    public void setDependencyVerificationMode(DependencyVerificationMode verificationMode) {
        this.verificationMode = verificationMode;
    }

    /**
     * Returns the dependency verification mode.
     *
     * @since 6.2
     */
    public DependencyVerificationMode getDependencyVerificationMode() {
        return verificationMode;
    }

    /**
     * Sets the key refresh flag.
     *
     * @param refresh If set to true, missing keys will be checked again. By default missing keys are cached for 24 hours.
     * @since 6.2
     */
    public void setRefreshKeys(boolean refresh) {
        isRefreshKeys = refresh;
    }

    /**
     * If true, Gradle will try to download missing keys again.
     *
     * @since 6.2
     */
    public boolean isRefreshKeys() {
        return isRefreshKeys;
    }

    /**
     * If true, after writing the dependency verification file, a public keyring
     * file will be generated with all keys seen during generation of the file.
     *
     * This file can then be used as a source for public keys instead of reaching
     * out public key servers.
     *
     * @return true if keys should be exported
     * @since 6.2
     */
    public boolean isExportKeys() {
        return isExportKeys;
    }

    /**
     * If true, after writing the dependency verification file, a public keyring
     * file will be generated with all keys seen during generation of the file.
     *
     * This file can then be used as a source for public keys instead of reaching
     * out public key servers.
     *
     * @param exportKeys set to true if keys should be exported
     * @since 6.2
     */
    public void setExportKeys(boolean exportKeys) {
        isExportKeys = exportKeys;
    }

    /**
     * Returns when to display a welcome message on the command line.
     *
     * @return The welcome message configuration.
     * @see WelcomeMessageDisplayMode
     * @since 7.5
     */
    @Incubating
    public WelcomeMessageConfiguration getWelcomeMessageConfiguration() {
        return welcomeMessageConfiguration;
    }

    /**
     * Updates when to display a welcome message on the command line.
     *
     * @param welcomeMessageConfiguration The welcome message configuration.
     * @see WelcomeMessageDisplayMode
     * @since 7.5
     */
    @Incubating
    public void setWelcomeMessageConfiguration(WelcomeMessageConfiguration welcomeMessageConfiguration) {
        this.welcomeMessageConfiguration = welcomeMessageConfiguration;
    }

    /**
     * Returns true if configuration caching has been requested. Note that the configuration cache may not necessarily be used even when requested, for example
     * it may be disabled due to the presence of configuration cache problems. It is also currently not used during an IDE import/sync.
     *
     * @since 7.6
     * @deprecated Use {@link org.gradle.api.configuration.BuildFeatures#getConfigurationCache() Configuration Cache build feature} instead.
     */
    @Incubating
    @Deprecated
    public boolean isConfigurationCacheRequested() {
        // TODO:configuration-cache add nagging in 8.6 (https://github.com/gradle/gradle/issues/26720)
        return false;
    }
}
