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

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.gradle.api.internal.artifacts.ProjectDependenciesBuildInstruction;
import org.gradle.api.logging.LogLevel;
import org.gradle.execution.*;
import org.gradle.groovy.scripts.UriScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.initialization.*;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

/**
 * <p>{@code StartParameter} defines the configuration used by a {@link GradleLauncher} instance to execute a build. The
 * properties of {@code StartParameter} generally correspond to the command-line options of Gradle. You pass a {@code
 * StartParameter} instance to {@link GradleLauncher#newInstance(StartParameter)} when you create a new {@code Gradle}
 * instance.</p>
 *
 * <p>You can obtain an instance of a {@code StartParameter} by either creating a new one, or duplicating an existing
 * one using {@link #newInstance} or {@link #newBuild}.</p>
 *
 * @author Hans Dockter
 * @see GradleLauncher
 */
public class StartParameter {
    public static final String GRADLE_USER_HOME_PROPERTY_KEY = "gradle.user.home";
    /**
     * The default user home directory.
     */
    public static final File DEFAULT_GRADLE_USER_HOME = new File(System.getProperty("user.home") + "/.gradle");

    /**
     * Specifies the detail to include in stacktraces.
     */
    public enum ShowStacktrace {
        INTERNAL_EXCEPTIONS, ALWAYS, ALWAYS_FULL
    }

    private List<String> taskNames = new ArrayList<String>();
    private Set<String> excludedTaskNames = new HashSet<String>();
    private ProjectDependenciesBuildInstruction projectDependenciesBuildInstruction
            = new ProjectDependenciesBuildInstruction(true);
    private File currentDir;
    private boolean searchUpwards = true;
    private Map<String, String> projectProperties = new HashMap<String, String>();
    private Map<String, String> systemPropertiesArgs = new HashMap<String, String>();
    private File gradleUserHomeDir;
    private CacheUsage cacheUsage = CacheUsage.ON;
    private ScriptSource buildScriptSource;
    private ScriptSource settingsScriptSource;
    private BuildExecuter buildExecuter;
    private ProjectSpec defaultProjectSelector;
    private LogLevel logLevel = LogLevel.LIFECYCLE;
    private ShowStacktrace showStacktrace = ShowStacktrace.INTERNAL_EXCEPTIONS;
    private File buildFile;
    private List<File> initScripts = new ArrayList<File>();
    private boolean dryRun;
    private boolean noOpt;
    private boolean colorOutput = true;
    private boolean profile;

    /**
     * Creates a {@code StartParameter} with default values. This is roughly equivalent to running Gradle on the
     * command-line with no arguments.
     */
    public StartParameter() {
        String gradleUserHome = System.getProperty(GRADLE_USER_HOME_PROPERTY_KEY);
        if (gradleUserHome == null) {
            gradleUserHome = System.getenv("GRADLE_USER_HOME");
            if (gradleUserHome == null) {
                gradleUserHome = DEFAULT_GRADLE_USER_HOME.getAbsolutePath();
            }
        }

        gradleUserHomeDir = GFileUtils.canonicalise(new File(gradleUserHome));
        setCurrentDir(null);
    }

    /**
     * Duplicates this {@code StartParameter} instance.
     *
     * @return the new parameters.
     */
    public StartParameter newInstance() {
        StartParameter startParameter = new StartParameter();
        startParameter.buildFile = buildFile;
        startParameter.taskNames = taskNames;
        startParameter.projectDependenciesBuildInstruction = projectDependenciesBuildInstruction;
        startParameter.currentDir = currentDir;
        startParameter.searchUpwards = searchUpwards;
        startParameter.projectProperties = projectProperties;
        startParameter.systemPropertiesArgs = systemPropertiesArgs;
        startParameter.gradleUserHomeDir = gradleUserHomeDir;
        startParameter.cacheUsage = cacheUsage;
        startParameter.buildScriptSource = buildScriptSource;
        startParameter.settingsScriptSource = settingsScriptSource;
        startParameter.initScripts = new ArrayList<File>(initScripts); 
        startParameter.buildExecuter = buildExecuter;
        startParameter.defaultProjectSelector = defaultProjectSelector;
        startParameter.logLevel = logLevel;
        startParameter.colorOutput = colorOutput;
        startParameter.showStacktrace = showStacktrace;
        startParameter.dryRun = dryRun;
        startParameter.noOpt = noOpt;
        startParameter.profile = profile;
        return startParameter;
    }

    /**
     * <p>Creates the parameters for a new build, using these parameters as a template. Copies the environmental
     * properties from this parameter (eg gradle user home dir, etc), but does not copy the build specific properties
     * (eg task names).</p>
     *
     * @return The new parameters.
     */
    public StartParameter newBuild() {
        StartParameter startParameter = new StartParameter();
        startParameter.gradleUserHomeDir = gradleUserHomeDir;
        startParameter.cacheUsage = cacheUsage;
        startParameter.logLevel = logLevel;
        startParameter.colorOutput = colorOutput;
        startParameter.profile = profile;
        return startParameter;
    }

    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * Returns the build file to use to select the default project. Returns null when the build file is not used to
     * select the default project.
     *
     * @return The build file. May be null.
     */
    public File getBuildFile() {
        return buildFile;
    }

    /**
     * Sets the build file to use to select the default project. Use null to disable selecting the default project using
     * the build file.
     *
     * @param buildFile The build file. May be null.
     */
    public void setBuildFile(File buildFile) {
        if (buildFile == null) {
            this.buildFile = null;
            setCurrentDir(null);
        } else {
            this.buildFile = GFileUtils.canonicalise(buildFile);
            currentDir = this.buildFile.getParentFile();
            defaultProjectSelector = new BuildFileProjectSpec(this.buildFile);
        }
    }

    /**
     * <p>Returns the {@link ScriptSource} to use for the build file for this build. Returns null when the default build
     * file(s) are to be used. This source is used for <em>all</em> projects included in the build.</p>
     *
     * @return The build file source, or null to use the defaults.
     */
    public ScriptSource getBuildScriptSource() {
        return buildScriptSource;
    }

    /**
     * <p>Returns the {@link ScriptSource} to use for the settings script for this build. Returns null when the default
     * settings script is to be used.</p>
     *
     * @return The settings script source, or null to use the default.
     */
    public ScriptSource getSettingsScriptSource() {
        return settingsScriptSource;
    }

    /**
     * <p>Sets the {@link ScriptSource} to use for the settings script. Set to null to use the default settings
     * script.</p>
     *
     * @param settingsScriptSource The settings script source.
     */
    public void setSettingsScriptSource(ScriptSource settingsScriptSource) {
        this.settingsScriptSource = settingsScriptSource;
    }

    /**
     * <p>Specifies that the given script should be used as the build file for this build. Uses an empty settings file.
     * </p>
     *
     * @param buildScriptText The script to use as the build file.
     * @return this
     */
    public StartParameter useEmbeddedBuildFile(String buildScriptText) {
        return setBuildScriptSource(new StringScriptSource("embedded build file", buildScriptText));
    }
    
    /**
     * <p>Specifies that the given script should be used as the build file for this build. Uses an empty settings file.
     * </p>
     *
     * @param buildScript The script to use as the build file.
     * @return this
     */
    public StartParameter setBuildScriptSource(ScriptSource buildScript) {
        buildScriptSource = buildScript;
        settingsScriptSource = new StringScriptSource("empty settings file", "");
        searchUpwards = false;
        return this;
    }

    /**
     * <p>Returns the {@link BuildExecuter} to use for the build.</p>
     *
     * @return The {@link BuildExecuter}. Never returns null.
     */
    public BuildExecuter getBuildExecuter() {
        BuildExecuter executer = buildExecuter;
        if (executer == null) {
            executer = new DefaultBuildExecuter(taskNames, excludedTaskNames);
        }
        if (dryRun) {
            executer = new DryRunBuildExecuter(executer);
        }
        return executer;
    }

    /**
     * <p>Sets the {@link BuildExecuter} to use for the build. You can use the method to change the algorithm used to
     * execute the build, by providing your own {@code BuildExecuter} implementation.</p>
     *
     * <p> Set to null to use the default executer. When this property is set to a non-null value, the taskNames and
     * mergedBuild properties are ignored.</p>
     *
     * @param buildExecuter The executer to use, or null to use the default executer.
     */
    public void setBuildExecuter(BuildExecuter buildExecuter) {
        this.buildExecuter = buildExecuter;
    }

    /**
     * Returns the names of the tasks to execute in this build. When empty, the default tasks for the project will be
     * executed.
     *
     * @return the names of the tasks to execute in this build. Never returns null.
     */
    public List<String> getTaskNames() {
        return taskNames;
    }

    /**
     * <p>Sets the tasks to execute in this build. Set to an empty list, or null, to execute the default tasks for the
     * project. The tasks are executed in the order provided, subject to dependency between the tasks.</p>
     *
     * @param taskNames the names of the tasks to execute in this build.
     */
    public void setTaskNames(Collection<String> taskNames) {
        this.taskNames = !GUtil.isTrue(taskNames) ? new ArrayList<String>() : new ArrayList<String>(taskNames);
        buildExecuter = null;
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
    public void setExcludedTaskNames(Collection<String> excludedTaskNames) {
        this.excludedTaskNames = !GUtil.isTrue(excludedTaskNames) ? new HashSet<String>() : new HashSet<String>(excludedTaskNames);
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
     * Sets the directory to use to select the default project, and to search for the settings file. Set to null to use
     * the default current directory.
     *
     * @param currentDir The directory. Should not be null.
     */
    public void setCurrentDir(File currentDir) {
        if (currentDir != null) {
            this.currentDir = GFileUtils.canonicalise(currentDir);
        } else {
            this.currentDir = GFileUtils.canonicalise(new File(System.getProperty("user.dir")));
        }
        defaultProjectSelector = null;
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
        this.gradleUserHomeDir = gradleUserHomeDir == null ? DEFAULT_GRADLE_USER_HOME : GFileUtils.canonicalise(gradleUserHomeDir);
    }

    public ProjectDependenciesBuildInstruction getProjectDependenciesBuildInstruction() {
        return projectDependenciesBuildInstruction;
    }

    public void setProjectDependenciesBuildInstruction(
            ProjectDependenciesBuildInstruction projectDependenciesBuildInstruction) {
        this.projectDependenciesBuildInstruction = projectDependenciesBuildInstruction;
    }

    public CacheUsage getCacheUsage() {
        return cacheUsage;
    }

    public void setCacheUsage(CacheUsage cacheUsage) {
        this.cacheUsage = cacheUsage;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isNoOpt() {
        return noOpt;
    }

    public void setNoOpt(boolean noOpt) {
        this.noOpt = noOpt;
    }

    /**
     * Sets the settings file to use for the build. Use null to use the default settings file.
     *
     * @param settingsFile The settings file to use. May be null.
     */
    public void setSettingsFile(File settingsFile) {
        if (settingsFile == null) {
            settingsScriptSource = null;
        } else {
            File canonicalFile = GFileUtils.canonicalise(settingsFile);
            currentDir = canonicalFile.getParentFile();
            settingsScriptSource = new UriScriptSource("settings file", canonicalFile);
        }
    }

    /**
     * Adds the given file to the list of init scripts that are run before the build starts.  This list is in
     * addition to the user init script located in ${user.home}/.gradle/init.gradle.
     * @param initScriptFile The init script to be run during the Gradle invocation.
     */
    public void addInitScript(File initScriptFile) {
        initScripts.add(initScriptFile);
    }

    public void setInitScripts(List<File> initScripts) {
        this.initScripts = initScripts;
    }

    /**
     * Returns all explicitly added init scripts that will be run before the build starts.  This list does not
     * contain the user init script located in ${user.home}/.gradle/init.gradle, even though that init script
     * will also be run.
     * @return list of all explicitly added init scripts.
     */
    public List<File> getInitScripts() {
        return Collections.unmodifiableList(initScripts);
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    public ShowStacktrace getShowStacktrace() {
        return showStacktrace;
    }

    public void setShowStacktrace(ShowStacktrace showStacktrace) {
        this.showStacktrace = showStacktrace;
    }

    /**
     * Returns the selector used to choose the default project of the build. This is the project used as the starting
     * point for resolving task names, and for determining the default tasks.
     *
     * @return The default project. Never returns null.
     */
    public ProjectSpec getDefaultProjectSelector() {
        return defaultProjectSelector != null ? defaultProjectSelector : new DefaultProjectSpec(currentDir);
    }

    /**
     * Sets the selector used to choose the default project of the build.
     *
     * @param defaultProjectSelector The selector. Should not be null.
     */
    public void setDefaultProjectSelector(ProjectSpec defaultProjectSelector) {
        this.defaultProjectSelector = defaultProjectSelector;
    }

    /**
     * Sets the project directory to use to select the default project. Use null to use the default criteria for
     * selecting the default project.
     *
     * @param projectDir The project directory. May be null.
     */
    public void setProjectDir(File projectDir) {
        if (projectDir == null) {
            setCurrentDir(null);
        } else {
            File canonicalFile = GFileUtils.canonicalise(projectDir);
            currentDir = canonicalFile;
            defaultProjectSelector = new ProjectDirectoryProjectSpec(canonicalFile);
        }
    }

    /**
     * Returns true if logging output should be displayed in color when Gradle is running in a terminal which supports
     * color output. The default value is true.
     *
     * @return true if logging output should be displayed in color.
     */
    public boolean isColorOutput() {
        return colorOutput;
    }

    /**
     * Specifies whether logging output should be displayed in color.
     *
     * @param colorOutput true if logging output should be displayed in color.
     */
    public void setColorOutput(boolean colorOutput) {
        this.colorOutput = colorOutput;
    }

    /**
     * Specifies if a profile report should be generated.
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

    @Override
    public String toString() {
        return "StartParameter{"
                + "taskNames=" + taskNames
                + ", excludedTaskNames=" + excludedTaskNames
                + ", currentDir=" + currentDir
                + ", searchUpwards=" + searchUpwards
                + ", projectProperties=" + projectProperties
                + ", systemPropertiesArgs=" + systemPropertiesArgs
                + ", gradleUserHomeDir=" + gradleUserHomeDir
                + ", cacheUsage=" + cacheUsage
                + ", buildScriptSource=" + buildScriptSource
                + ", settingsScriptSource=" + settingsScriptSource
                + ", buildExecuter=" + buildExecuter
                + ", defaultProjectSelector=" + defaultProjectSelector
                + ", logLevel=" + logLevel
                + ", showStacktrace=" + showStacktrace
                + ", buildFile=" + buildFile
                + ", initScripts=" + initScripts
                + ", dryRun=" + dryRun
                + ", noOpt=" + noOpt
                + ", profile=" + profile
                + '}';
    }
}