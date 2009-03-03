/*
 * Copyright 2007-2008 the original author or authors.
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
import org.gradle.api.logging.LogLevel;
import org.gradle.execution.TaskNameResolvingBuildExecuter;
import org.gradle.execution.ProjectDefaultsBuildExecuter;
import org.gradle.execution.BuildExecuter;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.util.GUtil;
import org.gradle.util.GFileUtils;
import org.gradle.initialization.ProjectSpec;
import org.gradle.initialization.ProjectDirectoryProjectSpec;
import org.gradle.initialization.BuildFileProjectSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>{@code StartParameter} defines the configuration used by a {@link Gradle} instance to execute a build. The
 * properties of {@code StartParameter} generally correspond to the command-line options of Gradle. You pass a {@code
 * StartParameter} instance to {@link Gradle#newInstance(StartParameter)} when you create a new {@code Gradle}
 * instance.</p>
 *
 * <p>You can obtain an instance of a {@code StartParameter} by either creating a new one, or duplicating an existing
 * one using {@link #newInstance} or {@link #newBuild}.</p>
 *
 * @author Hans Dockter
 * @see Gradle
 */
public class StartParameter {
    private List<String> taskNames = new ArrayList<String>();
    private File currentDir;
    private boolean searchUpwards = true;
    private Map<String, String> projectProperties = new HashMap<String, String>();
    private Map<String, String> systemPropertiesArgs = new HashMap<String, String>();
    private File gradleUserHomeDir = new File(Main.DEFAULT_GRADLE_USER_HOME);
    private File defaultImportsFile;
    private File pluginPropertiesFile;
    private File gradleHomeDir;
    private CacheUsage cacheUsage = CacheUsage.ON;
    private ScriptSource buildScriptSource;
    private ScriptSource settingsScriptSource;
    private BuildExecuter buildExecuter;
    private ProjectSpec defaultProjectSelector;
    private LogLevel logLevel = LogLevel.LIFECYCLE;
    private File buildFile;
    private File settingsFile;

    /**
     * Creates a {@code StartParameter} with default values. This is roughly equivalent to running Gradle on the
     * command-line with no arguments.
     */
    public StartParameter() {
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
        startParameter.currentDir = currentDir;
        startParameter.searchUpwards = searchUpwards;
        startParameter.projectProperties = projectProperties;
        startParameter.systemPropertiesArgs = systemPropertiesArgs;
        startParameter.gradleHomeDir = gradleHomeDir;
        startParameter.gradleUserHomeDir = gradleUserHomeDir;
        startParameter.defaultImportsFile = defaultImportsFile;
        startParameter.pluginPropertiesFile = pluginPropertiesFile;
        startParameter.cacheUsage = cacheUsage;
        startParameter.buildScriptSource = buildScriptSource;
        startParameter.settingsScriptSource = settingsScriptSource;
        startParameter.settingsFile = settingsFile;
        startParameter.buildExecuter = buildExecuter;
        startParameter.defaultProjectSelector = defaultProjectSelector;
        startParameter.logLevel = logLevel;

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
        startParameter.gradleHomeDir = gradleHomeDir;
        startParameter.gradleUserHomeDir = gradleUserHomeDir;
        startParameter.pluginPropertiesFile = pluginPropertiesFile;
        startParameter.defaultImportsFile = defaultImportsFile;
        startParameter.cacheUsage = cacheUsage;
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

    public File getGradleHomeDir() {
        return gradleHomeDir;
    }

    public void setGradleHomeDir(File gradleHomeDir) {
        this.gradleHomeDir = GFileUtils.canonicalise(gradleHomeDir);
        if (defaultImportsFile == null) {
            defaultImportsFile = new File(this.gradleHomeDir, Main.IMPORTS_FILE_NAME);
        }
        if (pluginPropertiesFile == null) {
            pluginPropertiesFile = new File(this.gradleHomeDir, Main.DEFAULT_PLUGIN_PROPERTIES);
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
        settingsFile = null;
    }

    /**
     * <p>Specifies that the given script should be used as the build file for this build. Uses an empty settings file.
     * </p>
     *
     * @param buildScript The script to use as the build file.
     * @return this
     */
    public StartParameter useEmbeddedBuildFile(String buildScript) {
        buildScriptSource = new StringScriptSource("embedded build file", buildScript);
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
        if (buildExecuter != null) {
            return buildExecuter;
        }
        return GUtil.isTrue(taskNames) ? new TaskNameResolvingBuildExecuter(taskNames)
                : new ProjectDefaultsBuildExecuter();
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
     * project.</p>
     *
     * @param taskNames the names of the tasks to execute in this build.
     */
    public void setTaskNames(List<String> taskNames) {
        this.taskNames = !GUtil.isTrue(taskNames) ? new ArrayList<String>() : new ArrayList<String>(taskNames);
        buildExecuter = null;
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
     * Sets the directory to use to select the default project, and to search for the settings file.
     *
     * @param currentDir The directory. Should not be null.
     */
    public void setCurrentDir(File currentDir) {
        if (currentDir != null) {
            this.currentDir = GFileUtils.canonicalise(currentDir);
        } else {
            this.currentDir = new File(System.getProperty("user.dir"));
        }
        defaultProjectSelector = new ProjectDirectoryProjectSpec(this.currentDir);
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

    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    public void setGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = GFileUtils.canonicalise(gradleUserHomeDir);
    }

    public File getDefaultImportsFile() {
        return defaultImportsFile;
    }

    public void setDefaultImportsFile(File defaultImportsFile) {
        this.defaultImportsFile = defaultImportsFile;
    }

    public File getPluginPropertiesFile() {
        return pluginPropertiesFile;
    }

    public void setPluginPropertiesFile(File pluginPropertiesFile) {
        this.pluginPropertiesFile = pluginPropertiesFile;
    }

    public CacheUsage getCacheUsage() {
        return cacheUsage;
    }

    public void setCacheUsage(CacheUsage cacheUsage) {
        this.cacheUsage = cacheUsage;
    }

    /**
     * Sets the settings file to use for the build. Use null to use the default settings file.
     *
     * @param settingsFile The settings file to use. May be null.
     */
    public void setSettingsFile(File settingsFile) {
        if (settingsFile == null) {
            this.settingsFile = null;
            settingsScriptSource = null;
        } else {
            this.settingsFile = GFileUtils.canonicalise(settingsFile);
            currentDir = this.settingsFile.getParentFile();
            settingsScriptSource = new FileScriptSource("settings file", this.settingsFile);
        }
    }

    /**
     * Returns the settings file to use for the build. Returns null when the default settings file is to be used.
     *
     * @return The settings file. May return null.
     */
    public File getSettingsFile() {
        return settingsFile;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * Returns the selector used to choose the default project of the build. This is the project used as the starting
     * point for resolving task names, and for determining the default tasks.
     *
     * @return The default project. Never returns null.
     */
    public ProjectSpec getDefaultProjectSelector() {
        return defaultProjectSelector;
    }

    /**
     * Sets the selector used to choose the default project of the build.
     *
     * @param defaultProjectSelector The selector. Should not be null.
     */
    public void setDefaultProjectSelector(ProjectSpec defaultProjectSelector) {
        this.defaultProjectSelector = defaultProjectSelector;
    }
}
