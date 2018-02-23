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
package org.gradle.api.plugins.quality;

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.resources.TextResource;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration options for the Checkstyle plugin.
 *
 * @see CheckstylePlugin
 */
public class CheckstyleExtension extends CodeQualityExtension {

    private final Project project;

    private TextResource config;
    private Map<String, Object> configProperties = new LinkedHashMap<String, Object>();
    private int maxErrors;
    private int maxWarnings = Integer.MAX_VALUE;
    private boolean showViolations = true;
    private File configDir;

    public CheckstyleExtension(Project project) {
        this.project = project;
    }

    /**
     * The Checkstyle configuration file to use.
     */
    public File getConfigFile() {
        return getConfig().asFile();
    }

    /**
     * The Checkstyle configuration file to use.
     */
    public void setConfigFile(File configFile) {
        setConfig(project.getResources().getText().fromFile(configFile));
    }

    /**
     * The Checkstyle configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    @Incubating
    public TextResource getConfig() {
        return config;
    }

    /**
     * The Checkstyle configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    @Incubating
    public void setConfig(TextResource config) {
        this.config = config;
    }

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    public Map<String, Object> getConfigProperties() {
        return configProperties;
    }

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    public void setConfigProperties(Map<String, Object> configProperties) {
        this.configProperties = configProperties;
    }

    /**
     * Path to other Checkstyle configuration files. By default, this path is {@code $projectDir/config/checkstyle}
     * <p>
     * This path will be exposed as the variable {@code config_loc} in Checkstyle's configuration files.
     * </p>
     * @return path to other Checkstyle configuration files
     * @since 4.0
     */
    @Incubating
    public File getConfigDir() {
        return configDir;
    }

    /**
     * Path to other Checkstyle configuration files. By default, this path is {@code $projectDir/config/checkstyle}
     * <p>
     * This path will be exposed as the variable {@code config_loc} in Checkstyle's configuration files.
     * </p>
     * @since 4.0
     */
    @Incubating
    public void setConfigDir(File configDir) {
        this.configDir = configDir;
    }

    /**
     * The maximum number of errors that are tolerated before breaking the build
     * or setting the failure property. Defaults to <tt>0</tt>.
     * <p>
     * Example: maxErrors = 42
     *
     * @since 3.4
     * @return the maximum number of errors allowed
     */
    public int getMaxErrors() {
        return maxErrors;
    }

    /**
     * Set the maximum number of errors that are tolerated before breaking the build.
     *
     * @since 3.4
     * @param maxErrors number of errors allowed
     */
    public void setMaxErrors(int maxErrors) {
        this.maxErrors = maxErrors;
    }

    /**
     * The maximum number of warnings that are tolerated before breaking the build
     * or setting the failure property. Defaults to <tt>Integer.MAX_VALUE</tt>.
     * <p>
     * Example: maxWarnings = 1000
     *
     * @since 3.4
     * @return the maximum number of warnings allowed
     */
    public int getMaxWarnings() {
        return maxWarnings;
    }

    /**
     * Set the maximum number of warnings that are tolerated before breaking the build.
     *
     * @since 3.4
     * @param maxWarnings number of warnings allowed
     */
    public void setMaxWarnings(int maxWarnings) {
        this.maxWarnings = maxWarnings;
    }

    /**
     * Whether rule violations are to be displayed on the console. Defaults to <tt>true</tt>.
     *
     * Example: showViolations = false
     */
    public boolean isShowViolations() {
        return showViolations;
    }

    /**
     * Whether rule violations are to be displayed on the console. Defaults to <tt>true</tt>.
     *
     * Example: showViolations = false
     */
    public void setShowViolations(boolean showViolations) {
        this.showViolations = showViolations;
    }
}
