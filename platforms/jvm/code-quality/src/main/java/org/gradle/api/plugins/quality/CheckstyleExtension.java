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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import java.io.File;

/**
 * Configuration options for the Checkstyle plugin.
 *
 * @see CheckstylePlugin
 */
public abstract class CheckstyleExtension extends CodeQualityExtension {

    private final Project project;

    private final TextResource config;
    private final DirectoryProperty configDirectory;
    private final Property<Boolean> enableExternalDtdLoad;

    public CheckstyleExtension(Project project) {
        this.project = project;
        this.configDirectory = project.getObjects().directoryProperty();
        this.enableExternalDtdLoad = project.getObjects().property(Boolean.class).convention(false);
        this.config = project.getResources().getText().fromFile(getConfigFile());
        getMaxErrors().convention(0);
        getMaxWarnings().convention(Integer.MAX_VALUE);
        getShowViolations().convention(true);
    }

    /**
     * The Checkstyle configuration file to use.
     */
    @ReplacesEagerProperty(adapter = ConfigFileAdapter.class)
    public abstract RegularFileProperty getConfigFile();

    static class ConfigFileAdapter {
        @BytecodeUpgrade
        static void setConfigFile(CheckstyleExtension extension, File configFile) {
            extension.getConfigFile().set(configFile);
        }

        @BytecodeUpgrade
        static File getConfigFile(CheckstyleExtension extension) {
            return extension.getConfigFile().getAsFile().getOrNull();
        }
    }

    /**
     * The Checkstyle configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    @NotToBeReplacedByLazyProperty(because = "TextResource is lazy")
    public TextResource getConfig() {
        return getConfigFile().isPresent() ? config : null;
    }

    /**
     * The Checkstyle configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    public void setConfig(TextResource config) {
        if (config == null) {
            getConfigFile().set((File) null);
        } else {
            getConfigFile().fileProvider(project.provider(config::asFile));
        }
    }

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    @ReplacesEagerProperty
    public abstract MapProperty<String, Object> getConfigProperties();

    /**
     * Path to other Checkstyle configuration files. By default, this path is {@code $rootProject.projectDir/config/checkstyle}
     * <p>
     * This path will be exposed as the variable {@code config_loc} in Checkstyle's configuration files.
     * </p>
     *
     * @return path to other Checkstyle configuration files
     * @since 4.7
     */
    public DirectoryProperty getConfigDirectory() {
        return configDirectory;
    }

    /**
     * The maximum number of errors that are tolerated before breaking the build
     * or setting the failure property. Defaults to <code>0</code>.
     * <p>
     * Example: maxErrors = 42
     *
     * @return the maximum number of errors allowed
     * @since 3.4
     */
    @ReplacesEagerProperty(originalType = int.class)
    public abstract Property<Integer> getMaxErrors();

    /**
     * The maximum number of warnings that are tolerated before breaking the build
     * or setting the failure property. Defaults to <code>Integer.MAX_VALUE</code>.
     * <p>
     * Example: maxWarnings = 1000
     *
     * @return the maximum number of warnings allowed
     * @since 3.4
     */
    @ReplacesEagerProperty(originalType = int.class)
    public abstract Property<Integer> getMaxWarnings();

    /**
     * Whether rule violations are to be displayed on the console. Defaults to <code>true</code>.
     *
     * Example: showViolations = false
     */
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getShowViolations();

    @Deprecated
    public Property<Boolean> getIsShowViolations() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsShowViolations()", "getShowViolations()");
        return getShowViolations();
    }

    /**
     * Enable the ability to use custom DTD files in config and load them from some location on all checkstyle tasks in this project.
     * <strong>Disabled by default due to security concerns.</strong>
     * See <a href="https://checkstyle.org/config_system_properties.html#Enable_External_DTD_load">Checkstyle documentation</a> for more details.
     *
     * @return The property controlling whether to enable the ability to use custom DTD files
     * @since 7.6
     */
    @Incubating
    @Optional
    @Input
    public Property<Boolean> getEnableExternalDtdLoad() {
        return enableExternalDtdLoad;
    }
}
