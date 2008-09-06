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
import org.gradle.api.Settings;
import org.gradle.api.Project;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
public class StartParameter {
    private String settingsFileName = Settings.DEFAULT_SETTINGS_FILE;
    private String buildFileName = Project.DEFAULT_BUILD_FILE;
    private List<String> taskNames = new ArrayList<String>();
    private File currentDir;
    private boolean searchUpwards;
    private Map<String, String> projectProperties = new HashMap<String, String>();
    private Map<String, String> systemPropertiesArgs = new HashMap<String, String>();
    private File gradleUserHomeDir;
    private File defaultImportsFile;
    private File pluginPropertiesFile;
    private File buildResolverDirectory;
    private CacheUsage cacheUsage;
    private StringScriptSource buildScriptSource;
    private StringScriptSource settingsScriptSource;

    public StartParameter() {
    }

    public StartParameter(String settingsFileName, String buildFileName, List<String> taskNames, File currentDir, boolean searchUpwards, Map<String, String> projectProperties, Map<String, String> systemPropertiesArgs, File gradleUserHomeDir, File defaultImportsFile, File pluginPropertiesFile, CacheUsage cacheUsage) {
        this.settingsFileName = settingsFileName;
        this.buildFileName = buildFileName;
        this.taskNames = taskNames;
        this.currentDir = currentDir;
        this.searchUpwards = searchUpwards;
        this.projectProperties = projectProperties;
        this.systemPropertiesArgs = systemPropertiesArgs;
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.defaultImportsFile = defaultImportsFile;
        this.pluginPropertiesFile = pluginPropertiesFile;
        this.cacheUsage = cacheUsage;
    }

    public static StartParameter newInstance(StartParameter startParameterSrc) {
        StartParameter startParameter = new StartParameter();
        startParameter.settingsFileName = startParameterSrc.settingsFileName;
        startParameter.buildFileName = startParameterSrc.buildFileName;
        startParameter.taskNames = startParameterSrc.taskNames;
        startParameter.currentDir = startParameterSrc.currentDir;
        startParameter.searchUpwards = startParameterSrc.searchUpwards;
        startParameter.projectProperties = startParameterSrc.projectProperties;
        startParameter.systemPropertiesArgs = startParameterSrc.systemPropertiesArgs;
        startParameter.gradleUserHomeDir = startParameterSrc.gradleUserHomeDir;
        startParameter.defaultImportsFile = startParameterSrc.defaultImportsFile;
        startParameter.pluginPropertiesFile = startParameterSrc.pluginPropertiesFile;
        startParameter.cacheUsage = startParameterSrc.cacheUsage;
        startParameter.buildResolverDirectory = startParameterSrc.buildResolverDirectory;
        startParameter.buildScriptSource = startParameterSrc.buildScriptSource;
        startParameter.settingsScriptSource = startParameterSrc.settingsScriptSource;

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

    public String getBuildFileName() {
        return buildFileName;
    }

    public void setBuildFileName(String buildFileName) {
        this.buildFileName = buildFileName;
    }

    /**
     * <p>Returns the {@link ScriptSource} to use for the build file. Returns null when the default build file(s) are to
     * be used.</p>
     *
     * @return The build file source, or null to use the defaults.
     */
    public ScriptSource getBuildScriptSource() {
        return buildScriptSource;
    }

    /**
     * <p>Returns the {@link ScriptSource} to use for the settings file. Returns null when the default settings file is
     * to be used.</p>
     *
     * @return The settings file source, or null to use the default.
     */
    public ScriptSource getSettingsScriptSource() {
        return settingsScriptSource;
    }

    /**
     * <p>Specifies that the given script should be used as the build file. Uses an empty settings file.</p>
     *
     * @param buildScript The script to use as the build file.
     * @return this
     */
    public StartParameter useEmbeddedBuildFile(String buildScript) {
        buildScriptSource = new StringScriptSource("embedded build file", buildScript);
        buildFileName = Project.EMBEDDED_SCRIPT_ID;
        settingsScriptSource = new StringScriptSource("empty settings file", "");
        return this;
    }

    public File getBuildResolverDirectory() {
        return buildResolverDirectory;
    }

    public void setBuildResolverDirectory(File buildResolverDirectory) {
        this.buildResolverDirectory = buildResolverDirectory;
    }

    public List<String> getTaskNames() {
        return taskNames;
    }

    public void setTaskNames(List<String> taskNames) {
        this.taskNames = taskNames;
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public void setCurrentDir(File currentDir) {
        this.currentDir = currentDir;
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
        this.gradleUserHomeDir = gradleUserHomeDir;
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

    public String getSettingsFileName() {
        return settingsFileName;
    }

    public void setSettingsFileName(String settingsFileName) {
        this.settingsFileName = settingsFileName;
    }
}
