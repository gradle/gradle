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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.quality.internal.CheckstyleAction;
import org.gradle.api.plugins.quality.internal.CheckstyleActionParameters;
import org.gradle.api.plugins.quality.internal.CheckstyleReportsImpl;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
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
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.internal.instrumentation.api.annotations.UpgradedProperty;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin.maybeAddOpensJvmArgs;

/**
 * Runs Checkstyle against some source files.
 */
@CacheableTask
public abstract class Checkstyle extends SourceTask implements VerificationTask, Reporting<CheckstyleReports> {

    private FileCollection checkstyleClasspath;
    private FileCollection classpath;
    private TextResource config;
    private Map<String, Object> configProperties = new LinkedHashMap<String, Object>();
    private final CheckstyleReports reports;
    private boolean ignoreFailures;
    private int maxWarnings = Integer.MAX_VALUE;
    private boolean showViolations = true;
    private final DirectoryProperty configDirectory;
    private final Property<JavaLauncher> javaLauncher;
    private final Property<String> minHeapSize;
    private final Property<String> maxHeapSize;
    private final Property<Boolean> enableExternalDtdLoad;

    public Checkstyle() {
        this.configDirectory = getObjectFactory().directoryProperty();
        this.reports = getObjectFactory().newInstance(CheckstyleReportsImpl.class, this);
        this.minHeapSize = getObjectFactory().property(String.class);
        this.maxHeapSize = getObjectFactory().property(String.class);
        this.enableExternalDtdLoad = getObjectFactory().property(Boolean.class).convention(false);
        // Set default JavaLauncher to current JVM in case
        // CheckstylePlugin that sets Java launcher convention is not applied
        this.javaLauncher = configureFromCurrentJvmLauncher(getToolchainService(), getObjectFactory());
        getMaxErrors().convention(0);
    }

    private static Property<JavaLauncher> configureFromCurrentJvmLauncher(JavaToolchainService toolchainService, ObjectFactory objectFactory) {
        Provider<JavaLauncher> currentJvmLauncherProvider = toolchainService.launcherFor(new CurrentJvmToolchainSpec(objectFactory));
        return objectFactory.property(JavaLauncher.class).convention(currentJvmLauncherProvider);
    }

    /**
     * The Checkstyle configuration file to use.
     */
    @Internal
    public File getConfigFile() {
        return getConfig() == null ? null : getConfig().asFile();
    }

    /**
     * The Checkstyle configuration file to use.
     */
    public void setConfigFile(File configFile) {
        setConfig(getProject().getResources().getText().fromFile(configFile));
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaToolchainService getToolchainService() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public WorkerExecutor getWorkerExecutor() {
        throw new UnsupportedOperationException();
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

    /**
     * JavaLauncher for toolchain support.
     *
     * @since 7.5
     */
    @Nested
    public Property<JavaLauncher> getJavaLauncher() {
        return javaLauncher;
    }

    @TaskAction
    public void run() {
        runWithProcessIsolation();
    }

    private void runWithProcessIsolation() {
        WorkQueue workQueue = getWorkerExecutor().processIsolation(spec -> {
            spec.getForkOptions().setMinHeapSize(minHeapSize.getOrNull());
            spec.getForkOptions().setMaxHeapSize(maxHeapSize.getOrNull());
            spec.getForkOptions().setExecutable(javaLauncher.get().getExecutablePath().getAsFile().getAbsolutePath());
            spec.getForkOptions().getSystemProperties().put("checkstyle.enableExternalDtdLoad", getEnableExternalDtdLoad().get());
            maybeAddOpensJvmArgs(javaLauncher.get(), spec);
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
        parameters.getShowViolations().set(isShowViolations());
        parameters.getSource().setFrom(getSource());
        parameters.getIsHtmlRequired().set(getReports().getHtml().getRequired());
        parameters.getIsXmlRequired().set(getReports().getXml().getRequired());
        parameters.getIsSarifRequired().set(getReports().getSarif().getRequired());
        parameters.getXmlOuputLocation().set(getReports().getXml().getOutputLocation());
        parameters.getHtmlOuputLocation().set(getReports().getHtml().getOutputLocation());
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
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * The class path containing the Checkstyle library to be used.
     */
    @Classpath
    public FileCollection getCheckstyleClasspath() {
        return checkstyleClasspath;
    }

    /**
     * The class path containing the Checkstyle library to be used.
     */
    public void setCheckstyleClasspath(FileCollection checkstyleClasspath) {
        this.checkstyleClasspath = checkstyleClasspath;
    }

    /**
     * The class path containing the compiled classes for the source files to be analyzed.
     */
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * The class path containing the compiled classes for the source files to be analyzed.
     */
    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

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
    @Nullable
    @Optional
    @Input
    public Map<String, Object> getConfigProperties() {
        return configProperties;
    }

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    public void setConfigProperties(@Nullable Map<String, Object> configProperties) {
        this.configProperties = configProperties;
    }

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
     * Whether or not this task will ignore failures and continue running the build.
     *
     * @return true if failures should be ignored
     */
    @Override
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * Whether this task will ignore failures and continue running the build.
     *
     * @return true if failures should be ignored
     */
    @Internal
    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * Whether this task will ignore failures and continue running the build.
     */
    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    /**
     * The maximum number of errors that are tolerated before breaking the build
     * or setting the failure property.
     *
     * @return the maximum number of errors allowed
     * @since 3.4
     */
    @Input
    @UpgradedProperty(originalType = int.class)
    public abstract Property<Integer> getMaxErrors();

    /**
     * The maximum number of warnings that are tolerated before breaking the build
     * or setting the failure property.
     *
     * @return the maximum number of warnings allowed
     * @since 3.4
     */
    @Input
    public int getMaxWarnings() {
        return maxWarnings;
    }

    /**
     * Set the maximum number of warnings that are tolerated before breaking the build.
     *
     * @param maxWarnings number of warnings allowed
     * @since 3.4
     */
    public void setMaxWarnings(int maxWarnings) {
        this.maxWarnings = maxWarnings;
    }

    /**
     * Whether rule violations are to be displayed on the console.
     *
     * @return true if violations should be displayed on console
     */
    @Console
    public boolean isShowViolations() {
        return showViolations;
    }

    /**
     * Whether rule violations are to be displayed on the console.
     */
    public void setShowViolations(boolean showViolations) {
        this.showViolations = showViolations;
    }

    /**
     * The minimum heap size for the Checkstyle worker process, if any.
     * Supports the units megabytes (e.g. "512m") and gigabytes (e.g. "1g").
     *
     * @return The minimum heap size. Value should be null if the default minimum heap size should be used.
     *
     * @since 7.5
     */
    @Optional
    @Input
    public Property<String> getMinHeapSize() {
        return minHeapSize;
    }

    /**
     * The maximum heap size for the Checkstyle worker process, if any.
     * Supports the units megabytes (e.g. "512m") and gigabytes (e.g. "1g").
     *
     * @return The maximum heap size. Value should be null if the default maximum heap size should be used.
     *
     * @since 7.5
     */
    @Optional
    @Input
    public Property<String> getMaxHeapSize() {
        return maxHeapSize;
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
}
