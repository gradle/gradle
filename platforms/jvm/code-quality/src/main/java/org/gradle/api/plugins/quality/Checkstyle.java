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
package org.gradle.api.plugins.quality;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.plugins.quality.internal.CheckstyleAction;
import org.gradle.api.plugins.quality.internal.CheckstyleActionParameters;
import org.gradle.api.plugins.quality.internal.CheckstyleReportsImpl;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.Describables;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.workers.WorkQueue;

import java.io.File;

/**
 * Runs Checkstyle against some source files.
 */
@CacheableTask
public abstract class Checkstyle extends AbstractCodeQualityTask implements Reporting<CheckstyleReports> {
    private TextResource config;
    private final CheckstyleReports reports;
    private final DirectoryProperty configDirectory;
    private final Property<Boolean> enableExternalDtdLoad;

    public Checkstyle() {
        super();
        this.configDirectory = getObjectFactory().directoryProperty();
        this.reports = getObjectFactory().newInstance(CheckstyleReportsImpl.class, Describables.quoted("Task", getIdentityPath()));
        this.enableExternalDtdLoad = getObjectFactory().property(Boolean.class).convention(false);
        getMaxErrors().convention(0);
        getMaxWarnings().convention(Integer.MAX_VALUE);
        getShowViolations().convention(true);
    }

    /**
     * The Checkstyle configuration file to use.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public File getConfigFile() {
        return getConfig() == null ? null : getConfig().asFile();
    }

    /**
     * The Checkstyle configuration file to use.
     */
    public void setConfigFile(File configFile) {
        setConfig(getProject().getResources().getText().fromFile(configFile));
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * checkstyleTask {
     *   reports {
     *     html {
     *       destination "build/checkstyle.html"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param closure The configuration
     * @return The reports container
     */
    @Override
    @SuppressWarnings("rawtypes")
    public CheckstyleReports reports(@DelegatesTo(value = CheckstyleReports.class, strategy = Closure.DELEGATE_FIRST) Closure closure) {
        return reports(new ClosureBackedAction<>(closure));
    }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * checkstyleTask {
     *   reports {
     *     html {
     *       destination "build/checkstyle.html"
     *     }
     *   }
     * }
     * </pre>
     *
     * @param configureAction The configuration
     * @return The reports container
     * @since 3.0
     */
    @Override
    public CheckstyleReports reports(Action<? super CheckstyleReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

    @TaskAction
    public void run() {
        runWithProcessIsolation();
    }

    private void runWithProcessIsolation() {
        WorkQueue workQueue = getWorkerExecutor().processIsolation(spec -> {
            configureForkOptions(spec.getForkOptions());
            spec.getForkOptions().getSystemProperties().put("checkstyle.enableExternalDtdLoad", enableExternalDtdLoad.get().toString());
        });
        workQueue.submit(CheckstyleAction.class, this::setupParameters);
    }

    private void setupParameters(CheckstyleActionParameters parameters) {
        parameters.getAntLibraryClasspath().setFrom(getCheckstyleClasspath());
        parameters.getConfig().set(getConfigFile());
        parameters.getMaxErrors().set(getMaxErrors());
        parameters.getMaxWarnings().set(getMaxWarnings());
        parameters.getIgnoreFailures().set(getIgnoreFailures());
        parameters.getConfigDirectory().set(getConfigDirectory());
        parameters.getShowViolations().set(getShowViolations());
        parameters.getSource().setFrom(getSource());
        parameters.getIsHtmlRequired().set(getReports().getHtml().getRequired());
        parameters.getIsXmlRequired().set(getReports().getXml().getRequired());
        parameters.getIsSarifRequired().set(getReports().getSarif().getRequired());
        parameters.getXmlOutputLocation().set(getReports().getXml().getOutputLocation());
        parameters.getHtmlOutputLocation().set(getReports().getHtml().getOutputLocation());
        parameters.getSarifOutputLocation().set(getReports().getSarif().getOutputLocation());
        parameters.getTemporaryDir().set(getTemporaryDir());
        parameters.getConfigProperties().set(getConfigProperties());
        TextResource stylesheetString = getReports().getHtml().getStylesheet();
        if (stylesheetString != null) {
            parameters.getStylesheetString().set(stylesheetString.asString());
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The sources for this task are relatively relocatable even though it produces output that
     * includes absolute paths. This is a compromise made to ensure that results can be reused
     * between different builds. The downside is that up-to-date results, or results loaded
     * from cache can show different absolute paths than would be produced if the task was
     * executed.</p>
     */
    @Override
    @ToBeReplacedByLazyProperty
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * The class path containing the Checkstyle library to be used.
     */
    @Classpath
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getCheckstyleClasspath();

    /**
     * The class path containing the compiled classes for the source files to be analyzed.
     */
    @Classpath
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * The Checkstyle configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    @Nested
    public TextResource getConfig() {
        return config;
    }

    /**
     * The Checkstyle configuration to use. Replaces the {@code configFile} property.
     *
     * @since 2.2
     */
    public void setConfig(TextResource config) {
        this.config = config;
    }

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract MapProperty<String, Object> getConfigProperties();

    /**
     * Path to other Checkstyle configuration files.
     * <p>
     * This path will be exposed as the variable {@code config_loc} in Checkstyle's configuration files.
     * </p>
     *
     * @return path to other Checkstyle configuration files
     * @since 6.0
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    public DirectoryProperty getConfigDirectory() {
        return configDirectory;
    }

    /**
     * The reports to be generated by this task.
     */
    @Override
    @Nested
    public final CheckstyleReports getReports() {
        return reports;
    }

    /**
     * The maximum number of errors that are tolerated before breaking the build
     * or setting the failure property.
     *
     * @return the maximum number of errors allowed
     * @since 3.4
     */
    @Input
    @ReplacesEagerProperty(originalType = int.class)
    public abstract Property<Integer> getMaxErrors();

    /**
     * The maximum number of warnings that are tolerated before breaking the build
     * or setting the failure property.
     *
     * @return the maximum number of warnings allowed
     * @since 3.4
     */
    @Input
    @ReplacesEagerProperty(originalType = int.class)
    public abstract Property<Integer> getMaxWarnings();

    /**
     * Whether rule violations are to be displayed on the console.
     *
     * @return true if violations should be displayed on console
     */
    @Console
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getShowViolations();

    @Internal
    @Deprecated
    public Property<Boolean> getIsShowViolations() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsShowViolations()", "getShowViolations()");
        return getShowViolations();
    }

    /**
     * Enable the use of external DTD files in configuration files.
     * <strong>Disabled by default because this may be unsafe.</strong>
     * See <a href="https://checkstyle.org/config_system_properties.html#Enable_External_DTD_load">Checkstyle documentation</a> for more details.
     *
     * @return property to enable the use of external DTD files
     *
     * @since 7.6
     */
    @Incubating
    @Input
    public Property<Boolean> getEnableExternalDtdLoad() {
        return enableExternalDtdLoad;
    }

    /**
     * Whether the build should break when the verifications performed by this task fail.
     *
     * @since 9.0
     */
    @Internal
    @Deprecated
    @ReplacesEagerProperty(adapter = IsIgnoreFailuresAdapter.class)
    public Property<Boolean> getIsIgnoreFailures() {
        ProviderApiDeprecationLogger.logDeprecation(getClass(), "getIsIgnoreFailures()", "getIgnoreFailures()");
        return getIgnoreFailuresProperty();
    }

    static class IsIgnoreFailuresAdapter {
        @BytecodeUpgrade
        static boolean isIgnoreFailures(Checkstyle task) {
            return task.getIgnoreFailures();
        }
    }
}
